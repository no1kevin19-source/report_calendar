package com.reportcalendar.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.credentials.*;
import androidx.credentials.exceptions.ClearCredentialException;
import com.google.android.gms.tasks.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/** One process owner for Auth requests. Activities observe only while resumed. */
final class AuthController {
    interface Observer { void changed(); }
    private static AuthController instance;
    static synchronized AuthController get(Context context) {
        if (instance == null) instance = new AuthController(context.getApplicationContext());
        return instance;
    }
    final AuthOperationState state = new AuthOperationState();
    final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Observer> observers = new HashSet<>();
    private final SharedPreferences registration;
    private long verificationRetryAt;
    private boolean checkedRecovery;

    private AuthController(Context context) {
        this.context = context;
        registration = context.getSharedPreferences("auth_registration_recovery", Context.MODE_PRIVATE);
        if (registration.contains("app")) {
            String name = registration.getString("app", "");
            FirebaseApp isolated;
            try { isolated = FirebaseApp.getInstance(name); }
            catch (IllegalStateException absent) {
                isolated = FirebaseApp.initializeApp(context, FirebaseApp.getInstance().getOptions(), name);
            }
            // Clean only the interrupted operation's private session, never the main session.
            FirebaseAuth.getInstance(isolated).signOut();
            isolated.delete();
            state.kind = "register";
            state.email = registration.getString("email", "");
            state.accountCreated = registration.getBoolean("created", false);
            state.message = state.accountCreated
                ? "이미 가입된 계정이에요. 다시 가입하지 말고 로그인해 주세요."
                : "가입이 완료됐는지 확인하지 못했어요. 먼저 로그인해 보세요. 비밀번호가 기억나지 않으면 재설정할 수 있어요.";
            registration.edit().clear().commit();
        }
        auth.addAuthStateListener(ignored -> publish());
    }
    Executor executor() { return command -> main.post(command); }
    void observe(Observer observer) { observers.add(observer); observer.changed(); }
    void remove(Observer observer) { observers.remove(observer); }
    private void publish() { for (Observer observer : new ArrayList<>(observers)) observer.changed(); }

    private long begin(String kind, String email) {
        long id = state.begin(kind, email);
        if (id != 0) { publish(); watch(id, state.phase); }
        return id;
    }
    private void progress(long id, String phase, String message) {
        if (!state.accepts(id)) return;
        state.progress(id, phase, message); publish(); watch(id, phase);
    }
    private void finish(long id, String message) {
        if (!state.accepts(id)) return;
        state.finish(id, message); publish();
    }
    private void watch(long id, String phase) {
        main.postDelayed(() -> {
            if (!state.accepts(id) || !state.phase.equals(phase)) return;
            state.progress(id, phase, state.message
                + "\n조금 오래 걸리고 있어요. 아직 완료 여부는 알 수 없어요. 다시 누르지 않아도 계속 처리돼요.");
            publish();
        }, 20_000);
    }

    void signIn(String address, String password) {
        long id = begin("login", address);
        if (id == 0) return;
        completeSignIn(id, auth.signInWithEmailAndPassword(address, password));
    }
    Task<AuthResult> signInGuest() {
        long id = begin("guest", "");
        if (id == 0) return Tasks.forException(new IllegalStateException("Authentication in progress"));
        Task<AuthResult> task = auth.signInAnonymously();
        task.addOnCompleteListener(done -> finish(id, done.isSuccessful() ? "" : error(done.getException())));
        return task;
    }
    private void completeSignIn(long id, Task<AuthResult> task) {
        task.addOnCompleteListener(done -> {
            if (!state.accepts(id)) return;
            if (!done.isSuccessful()) { finish(id, error(done.getException())); return; }
            FirebaseUser user = done.getResult().getUser();
            if (user == null) { finish(id, "로그인 상태를 확인할 수 없어요."); return; }
            if (user.getUid().equals(pendingDeletionUid())) {
                finish(id, "로그인했어요. 이전에 요청한 계정 삭제 상태를 확인해 주세요.");
                return;
            }
            if (!user.isEmailVerified() || user.isAnonymous()) {
                finish(id, "로그인했어요. 메일의 인증 링크를 누른 뒤 ‘인증 완료 확인’을 눌러 주세요.");
                return;
            }
            progress(id, "profile", "로그인했어요. 내 정보를 저장하고 있어요.");
            Tasks.withTimeout(CloudStore.saveProfile(FirebaseApp.getInstance(), user), 15, TimeUnit.SECONDS)
                .addOnCompleteListener(saved -> finish(id, saved.isSuccessful() ? "로그인했어요."
                    : "로그인했어요. 내 정보가 저장됐는지는 확인하지 못했어요. ‘인증 완료 확인’을 눌러 다시 확인해 주세요."));
        });
    }
    void register(String address, String password) {
        long id = begin("register", address);
        if (id == 0) return;
        String name = "registration-" + UUID.randomUUID();
        if (!registration.edit().putString("app", name).putString("email", address)
                .putBoolean("created", false).commit()) {
            finish(id, "가입을 시작하지 못했어요. 휴대폰의 저장 공간을 확인해 주세요."); return;
        }
        FirebaseApp app = FirebaseApp.initializeApp(context, FirebaseApp.getInstance().getOptions(), name);
        FirebaseAuth separate = FirebaseAuth.getInstance(app);
        separate.setLanguageCode("ko");
        separate.createUserWithEmailAndPassword(address, password).addOnCompleteListener(created -> {
            if (!created.isSuccessful()) {
                cleanupRegistration(app, separate, name);
                finish(id, error(created.getException())); return;
            }
            state.accountCreated = true;
            registration.edit().putBoolean("created", true).commit();
            progress(id, "register-followup", "가입했어요. 인증 메일을 요청하고 있어요.");
            FirebaseUser user = created.getResult().getUser();
            Task<Void> rawMail = user.sendEmailVerification();
            Task<Void> mail = Tasks.withTimeout(rawMail, 15, TimeUnit.SECONDS);
            mail.addOnCompleteListener(done -> {
                finish(id, "가입했어요! 이제 로그인해 주세요.\n"
                    + verificationResult(address, mail.isSuccessful(), mail.getException())
                    + "\n메일이 안 오면 로그인 후 마이페이지에서 다시 보내 주세요.");
            });
            // A wrapper timeout does not cancel the email request. Clean its isolated session only.
            rawMail.addOnCompleteListener(done -> cleanupRegistration(app, separate, name));
        });
    }
    private void cleanupRegistration(FirebaseApp app, FirebaseAuth separate, String name) {
        separate.signOut();
        if (name.equals(registration.getString("app", ""))) registration.edit().clear().commit();
        app.delete(); // Registration never initializes Firestore.
    }
    void resetPassword(String address) {
        long id = begin("reset", address);
        if (id == 0) return;
        auth.setLanguageCode("ko");
        auth.sendPasswordResetEmail(address).addOnCompleteListener(done -> finish(id, done.isSuccessful()
            ? "가입한 이메일이라면 비밀번호 변경 메일을 받을 수 있어요. 메일함을 확인해 주세요." : error(done.getException())));
    }
    void updateProfile(String name, String photo, String expectedUid) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.isAnonymous() || !user.getUid().equals(expectedUid) || state.busy) return;
        long id = begin("profile-edit", user.getEmail());
        if (id == 0) return;
        if (name.trim().isEmpty() || name.length() > 60) { finish(id, "이름은 1~60자로 입력해 주세요."); return; }
        if (user.getUid().equals(pendingDeletionUid())) { finish(id, "먼저 ‘계정 삭제 상태 확인’을 눌러 주세요."); return; }
        progress(id, "profile-name", "프로필을 저장하고 있어요.");
        user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())
            .addOnCompleteListener(done -> {
                if (!state.accepts(id)) return;
                if (!done.isSuccessful()) { finish(id, "이름이 저장됐는지 확인하지 못했어요.\n" + error(done.getException())); return; }
                boolean avatarSaved = ProfilePhotos.save(context, expectedUid, photo);
                String notice = avatarSaved ? " 사진은 이 휴대폰에 저장했어요." : " 사진을 저장하지 못했어요. 다시 선택해 주세요.";
                if (!user.isEmailVerified()) {
                    finish(id, "이름을 저장했어요." + notice + " 내 정보 저장을 마치려면 이메일을 인증해 주세요."); return;
                }
                progress(id, "profile", "이름을 바꿨어요. 내 정보를 저장하고 있어요.");
                Tasks.withTimeout(CloudStore.saveProfile(FirebaseApp.getInstance(), user), 15, TimeUnit.SECONDS)
                    .addOnCompleteListener(saved -> finish(id, (saved.isSuccessful() ? "프로필을 저장했어요." :
                        "이름을 바꿨어요. 내 정보 저장은 ‘인증 완료 확인’을 눌러 다시 확인해 주세요.") + notice));
            });
    }
    void sendVerification() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        long id = begin("mail", user.getEmail());
        if (id == 0) return;
        long remaining = verificationRetryAt - SystemClock.elapsedRealtime();
        if (remaining > 0) {
            finish(id, ((remaining + 999) / 1000) + "초 뒤에 다시 요청해주세요."); return;
        }
        auth.setLanguageCode("ko");
        user.sendEmailVerification().addOnCompleteListener(done -> {
            verificationRetryAt = SystemClock.elapsedRealtime() + 60_000;
            finish(id, verificationResult(user.getEmail(), done.isSuccessful(), done.getException()));
        });
    }
    void checkVerification() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        long id = begin("verify", user.getEmail());
        if (id == 0) return;
        user.reload().addOnCompleteListener(done -> {
            if (!state.accepts(id)) return;
            if (!done.isSuccessful()) { finish(id, error(done.getException())); return; }
            if (!user.isEmailVerified()) {
                finish(id, "아직 인증되지 않았어요. 메일의 링크를 눌러주세요."); return;
            }
            if (user.getUid().equals(pendingDeletionUid())) {
                finish(id, "이메일을 인증했어요. 계정 삭제가 진행 중이라 내 정보는 저장하지 않았어요. ‘계정 삭제 상태 확인’을 눌러 주세요."); return;
            }
            progress(id, "profile", "인증했어요. 내 정보를 저장하고 있어요.");
            Tasks.withTimeout(CloudStore.saveProfile(FirebaseApp.getInstance(), user), 15, TimeUnit.SECONDS)
                .addOnCompleteListener(saved -> finish(id, saved.isSuccessful()
                    ? "이메일 인증과 내 정보 저장을 마쳤어요."
                    : "이메일은 인증됐어요. 내 정보 저장은 확인하지 못했으니 ‘인증 완료 확인’을 다시 눌러 주세요."));
        });
    }

    long beginGoogle(boolean deleting) {
        long id = begin(deleting ? "delete" : "google", "");
        if (id != 0) progress(id, "picker", deleting ? "삭제할 Google 계정을 선택해 주세요." : "Google 계정을 선택해주세요.");
        return id;
    }
    boolean isPicker(long id) { return state.accepts(id) && "picker".equals(state.phase); }
    void cancelPicker(long id) {
        if (isPicker(id)) finish(id, "Google 계정 선택이 취소되었어요. 다시 시도해주세요.");
    }
    void googleFailure(long id, boolean canceled) {
        if (isPicker(id)) finish(id, canceled ? "Google 계정 선택을 취소했어요." : "Google로 로그인하지 못했어요. 인터넷 연결과 선택한 계정을 확인해 주세요.");
    }
    void googleCredential(long id, AuthCredential credential) {
        if (!isPicker(id)) return;
        progress(id, "firebase", "Google 계정을 확인하고 있어요.");
        FirebaseUser user = auth.getCurrentUser();
        if ("delete".equals(state.kind)) {
            if (user == null || user.isAnonymous()) { finish(id, "삭제할 계정으로 다시 로그인해주세요."); return; }
            reauthenticateAndDelete(id, user, credential);
        } else if (user != null && user.isAnonymous()) {
            user.linkWithCredential(credential).addOnCompleteListener(linked -> {
                if (!state.accepts(id)) return;
                if (!linked.isSuccessful() && linked.getException() instanceof FirebaseAuthUserCollisionException)
                    completeSignIn(id, auth.signInWithCredential(credential));
                else completeSignIn(id, linked);
            });
        } else completeSignIn(id, auth.signInWithCredential(credential));
    }

    String pendingDeletionUid() { return DeletionJournal.pendingUid(context); }
    void deleteWithPassword(String password) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;
        long id = begin("delete", user.getEmail());
        if (id != 0) reauthenticateAndDelete(id, user, EmailAuthProvider.getCredential(user.getEmail(), password));
    }
    private void reauthenticateAndDelete(long id, FirebaseUser user, AuthCredential credential) {
        if (!pendingDeletionUid().isEmpty() && !pendingDeletionUid().equals(user.getUid())) {
            finish(id, "다른 계정의 삭제가 아직 끝나지 않았어요. 그 계정으로 로그인해 먼저 확인해 주세요."); return;
        }
        progress(id, "reauth", "본인인지 확인하고 있어요. 계정은 아직 삭제하지 않았어요.");
        user.reauthenticate(credential).addOnCompleteListener(done -> {
            if (!state.accepts(id)) return;
            if (!done.isSuccessful()) { finish(id, "본인 확인이 안 돼 계정을 삭제하지 않았어요.\n" + error(done.getException())); return; }
            FirebaseUser current = auth.getCurrentUser();
            if (current == null || !current.getUid().equals(user.getUid())) {
                finish(id, "로그인 계정이 변경되어 삭제를 중단했어요."); return;
            }
            boolean ambiguous = user.getUid().equals(pendingDeletionUid())
                && new DeletionJournal(context, user.getUid()).stage() == AccountDeletionFlow.Stage.AUTH_DELETE;
            deletionFlow(id, user).delete(ambiguous);
        });
    }
    private AccountDeletionFlow deletionFlow(long id, FirebaseUser user) {
        return new AccountDeletionFlow(CloudStore.deletionBackend(user), new DeletionJournal(context, user.getUid()),
            new AccountDeletionFlow.Listener() {
                public void progress(String phase, String message) { AuthController.this.progress(id, phase, message); }
                public void finished(boolean deleted, String message) {
                    if (deleted) {
                        FirebaseUser current = auth.getCurrentUser();
                        if (current != null && user.getUid().equals(current.getUid())) auth.signOut();
                        clearProviderSession();
                    }
                    finish(id, message);
                }
            });
    }
    void reconcileDeletion() {
        if (state.busy || pendingDeletionUid().isEmpty()) return;
        FirebaseUser user = auth.getCurrentUser();
        long id = begin("recovery", "");
        if (user == null || !pendingDeletionUid().equals(user.getUid())) {
            finish(id, "계정이 삭제됐는지 확인이 필요해요. 해당 계정으로 로그인해 주세요. 로그인할 수 없다면 앱 관리자에게 문의해 주세요."); return;
        }
        DeletionJournal journal = new DeletionJournal(context, user.getUid());
        deletionFlow(id, user).reconcile(journal.stage());
    }
    void foreground() {
        if (!checkedRecovery && !state.busy && !pendingDeletionUid().isEmpty()) {
            checkedRecovery = true; reconcileDeletion();
        }
    }
    void signOut() {
        long id = begin("logout", "");
        if (id == 0) return;
        auth.signOut();
        clearProviderSession();
        finish(id, "로그아웃했어요. 휴대폰에 저장한 수행평가는 그대로예요.");
    }
    private void clearProviderSession() {
        try {
            CredentialManager.create(context).clearCredentialStateAsync(new ClearCredentialStateRequest(), null, executor(),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    public void onResult(Void unused) { }
                    public void onError(ClearCredentialException error) { }
                });
        } catch (RuntimeException ignored) { /* Firebase sign-out has already completed. */ }
    }
    static String error(Exception exception) {
        if (exception instanceof com.google.firebase.FirebaseNetworkException) return "인터넷 연결을 확인해주세요.";
        if (exception instanceof com.google.firebase.FirebaseTooManyRequestsException) return "요청이 많아요. 잠시 후 다시 시도해주세요.";
        if (exception instanceof FirebaseAuthException) {
            switch (((FirebaseAuthException) exception).getErrorCode()) {
                case "ERROR_EMAIL_ALREADY_IN_USE": case "ERROR_CREDENTIAL_ALREADY_IN_USE":
                    return "이미 사용 중인 계정이에요. 로그인 또는 비밀번호 재설정을 이용해주세요.";
                case "ERROR_WEAK_PASSWORD": return "더 안전한 비밀번호를 입력해주세요.";
                case "ERROR_OPERATION_NOT_ALLOWED": return "이 로그인 방식은 아직 준비 중이에요.";
                case "ERROR_USER_DISABLED": return "사용이 중지된 계정이에요.";
                case "ERROR_REQUIRES_RECENT_LOGIN": return "다시 로그인한 뒤 시도해 주세요.";
                case "ERROR_USER_MISMATCH": return "현재 로그인한 계정과 같은 계정을 선택해주세요.";
                case "ERROR_INVALID_CREDENTIAL": case "ERROR_WRONG_PASSWORD": case "ERROR_USER_NOT_FOUND":
                    return "이메일 또는 비밀번호를 확인해주세요.";
            }
        }
        return "처리 결과를 확인하지 못했어요. 입력 내용과 연결 상태를 확인해주세요.";
    }
    static String verificationResult(String address, boolean success, Exception error) {
        return success ? "인증 메일을 보내도록 요청했어요.\n받는 주소: " + address + "\n메일의 인증 링크를 눌러 주세요. 안 보이면 스팸함도 확인해 주세요."
            : "인증 메일 전송을 확인하지 못했어요.\n" + error(error);
    }
}
