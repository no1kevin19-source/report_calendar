package com.reportcalendar.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.Patterns;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.credentials.*;
import androidx.credentials.exceptions.*;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.*;
import com.google.android.gms.tasks.Task;
import java.util.ArrayList;

/** Authentication is separate from the calendar so opening it preserves the task draft. */
public class AuthActivity extends Activity {
    protected boolean isMyPage() { return false; }
    private FirebaseAuth auth;
    private CredentialManager credentials;
    private LinearLayout content;
    private EditText email, password, confirm;
    private TextView status;
    private boolean registering;
    private boolean busy;
    private long verificationRetryAt;
    private CancellationSignal pendingGoogle;
    private final ArrayList<View> controls = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        auth = FirebaseAuth.getInstance();
        credentials = CredentialManager.create(this);
        FirebaseUser current = auth.getCurrentUser();
        boolean loggedIn = current != null && !current.isAnonymous();
        if (isMyPage() != loggedIn) {
            startActivity(new Intent(this, loggedIn ? MyPageActivity.class : AuthActivity.class));
            finish();
            return;
        }
        registering = state != null && state.getBoolean("registering");
        render();
        if (state != null && email != null) email.setText(state.getString("email", ""));
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("registering", registering);
        if (email != null) state.putString("email", email.getText().toString());
        // Passwords and Google tokens are never written to saved state or preferences.
    }

    private void render() {
        controls.clear();
        email = password = confirm = null;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.app_background));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(24));
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        button("캘린더로 돌아가기", false, this::finish);
        FirebaseUser user = auth.getCurrentUser();
        if (isMyPage() && user != null && !user.isAnonymous()) {
            label("마이페이지", 28);
            label(user.getEmail() == null ? "로그인됨" : user.getEmail(), 18);
            label("로그인되어 있어요. 이 기기에 저장된 수행평가는 그대로 유지됩니다.", 16);
            status = label("", 16);
            label(user.isEmailVerified() ? "이메일 인증 완료" : "이메일 미인증 · 메일함과 스팸함을 확인해주세요.", 16);
            if (!user.isEmailVerified()) {
                button("인증 메일 다시 보내기", false, () -> {
                    long remaining = verificationRetryAt - android.os.SystemClock.elapsedRealtime();
                    if (remaining > 0) {
                        status.setText("반복 전송을 피하려면 " + ((remaining + 999) / 1000) + "초 뒤에 다시 요청해주세요.");
                        return;
                    }
                    setBusy(true);
                    auth.setLanguageCode("ko");
                    user.sendEmailVerification().addOnCompleteListener(this, task -> {
                        setBusy(false);
                        verificationRetryAt = android.os.SystemClock.elapsedRealtime() + 60_000;
                        status.setText(verificationResult(user.getEmail(), task.isSuccessful(), task.getException()));
                    });
                });
                button("인증 완료 확인", false, () -> {
                    setBusy(true);
                    user.reload().addOnCompleteListener(this, task -> {
                        setBusy(false);
                        if (task.isSuccessful()) {
                            render();
                            status.setText(user.isEmailVerified() ? "이메일 인증이 확인되었어요." : "아직 인증되지 않았어요. 메일의 링크를 눌러주세요.");
                        } else status.setText(error(task.getException()));
                    });
                });
            }
            boolean passwordAccount = false;
            for (UserInfo provider : user.getProviderData()) {
                if ("password".equals(provider.getProviderId())) passwordAccount = true;
            }
            if (passwordAccount) button("비밀번호 재설정", false, () -> sendPasswordReset(user.getEmail()));
            else label("Google 계정의 비밀번호는 Google 계정 설정에서 변경할 수 있어요.", 14);
            button("계정 삭제", false, () -> confirmAccountDeletion(user));
            button("로그아웃", true, () -> new AlertDialog.Builder(this)
                .setMessage("로그아웃할까요? 이 기기의 수행평가는 삭제되지 않습니다.")
                .setNegativeButton("취소", null).setPositiveButton("로그아웃", (d, w) -> signOut()).show());
            setContentView(scroll);
            return;
        }
        label(registering ? "이메일 회원가입" : "로그인", 28);
        label("이메일과 비밀번호 또는 Google 계정으로 시작하세요.", 16);
        email = field("이메일", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        password = field("비밀번호", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (registering) confirm = field("비밀번호 확인", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        status = label("", 16);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        button(registering ? "회원가입" : "이메일로 로그인", true, this::submitEmail);
        if (!registering) button("Google 계정으로 로그인", false, this::signInGoogle);
        button(registering ? "이미 계정이 있어요 · 로그인" : "이메일로 회원가입", false, () -> {
            String address = email.getText().toString();
            registering = !registering;
            render();
            email.setText(address);
        });
        button("비밀번호 재설정", false, this::resetPassword);
        label("로그인만으로 수행평가가 자동으로 동기화되지는 않아요.", 14);
        // Attach the populated screen atomically, including after account changes.
        setContentView(scroll);
    }

    private boolean validEmail() {
        if (!Patterns.EMAIL_ADDRESS.matcher(email.getText().toString().trim()).matches()) {
            email.setError("올바른 이메일 주소를 입력해주세요.");
            email.requestFocus();
            return false;
        }
        return true;
    }

    private void submitEmail() {
        if (busy || !validEmail()) return;
        String address = email.getText().toString().trim();
        String secret = password.getText().toString();
        if (secret.isEmpty() || (registering && secret.length() < 6)) {
            password.setError(registering ? "비밀번호를 6자 이상 입력해주세요." : "비밀번호를 입력해주세요.");
            return;
        }
        if (registering && !secret.equals(confirm.getText().toString())) {
            confirm.setError("비밀번호가 일치하지 않아요.");
            return;
        }
        setBusy(true);
        if (registering) {
            registerAccount(address, secret);
            return;
        }
        completeSignIn(auth.signInWithEmailAndPassword(address, secret));
    }

    private void registerAccount(String address, String secret) {
        // Firebase signs in newly created users automatically. An isolated auth instance
        // keeps registration from changing the calendar's actual login session.
        com.google.firebase.FirebaseApp registrationApp;
        try {
            registrationApp = com.google.firebase.FirebaseApp.getInstance("registration");
        } catch (IllegalStateException missing) {
            registrationApp = com.google.firebase.FirebaseApp.initializeApp(getApplicationContext(),
                com.google.firebase.FirebaseApp.getInstance().getOptions(), "registration");
        }
        FirebaseAuth registrationAuth = FirebaseAuth.getInstance(registrationApp);
        final com.google.firebase.FirebaseApp profileApp = registrationApp;
        registrationAuth.setLanguageCode("ko");
        registrationAuth.createUserWithEmailAndPassword(address, secret).addOnCompleteListener(created -> {
            if (!created.isSuccessful()) {
                registrationAuth.signOut();
                if (!isDestroyed() && !isFinishing()) {
                    setBusy(false);
                    status.setText(error(created.getException()));
                }
                return;
            }
            FirebaseUser createdUser = created.getResult().getUser();
            Task<Void> profile = com.google.android.gms.tasks.Tasks.withTimeout(
                CloudStore.saveProfile(profileApp, createdUser), 15, java.util.concurrent.TimeUnit.SECONDS);
            Task<Void> sent = createdUser.sendEmailVerification();
            com.google.android.gms.tasks.Tasks.whenAllComplete(profile, sent).addOnCompleteListener(done -> {
                // Cleanup also runs if the Activity was closed while the request was in flight.
                registrationAuth.signOut();
                if (isDestroyed() || isFinishing()) return;
                busy = false;
                registering = false;
                render();
                email.setText(address);
                status.setText("회원가입이 완료되었어요. 직접 로그인해주세요.\n"
                    + verificationResult(address, sent.isSuccessful(), sent.getException())
                    + (profile.isSuccessful() ? "" : "\n사용자 정보 저장은 로그인 후 다시 시도합니다.")
                    + "\n다시 보내기는 로그인 후 마이페이지에서 할 수 있어요.");
            });
        });
    }

    private void completeSignIn(Task<AuthResult> task) {
        task.addOnCompleteListener(this, result -> {
            setBusy(false);
            if (result.isSuccessful()) {
                CloudStore.saveProfile(com.google.firebase.FirebaseApp.getInstance(), result.getResult().getUser())
                    .addOnFailureListener(e -> Toast.makeText(getApplicationContext(),
                        "사용자 정보 저장에 실패했어요. 다음 로그인 때 다시 시도해요.", Toast.LENGTH_LONG).show());
                startActivity(new Intent(this, MyPageActivity.class));
                finish();
            } else status.setText(error(result.getException()));
        });
    }

    private void resetPassword() {
        if (busy || !validEmail()) return;
        sendPasswordReset(email.getText().toString().trim());
    }

    private void sendPasswordReset(String address) {
        if (busy || address == null) return;
        setBusy(true);
        auth.setLanguageCode("ko");
        auth.sendPasswordResetEmail(address).addOnCompleteListener(this, task -> {
            setBusy(false);
            status.setText(task.isSuccessful() ? "등록된 계정이라면 재설정 메일이 전송됩니다. 메일함을 확인해주세요." : error(task.getException()));
        });
    }

    private void confirmAccountDeletion(FirebaseUser user) {
        if (busy) return;
        new AlertDialog.Builder(this).setTitle("계정을 삭제할까요?")
            .setMessage("로그인 계정과 Firestore 사용자 정보가 함께 삭제되며 되돌릴 수 없어요. 이 기기에 저장된 수행평가는 유지됩니다. 인터넷 연결이 필요해요.")
            .setNegativeButton("취소", null)
            .setPositiveButton("계정 영구 삭제", (dialog, which) -> {
                setBusy(true);
                CloudStore.deleteAccount(user).addOnCompleteListener(this, task -> {
                    setBusy(false);
                    if (task.isSuccessful()) {
                        auth.signOut();
                        clearProviderSession(getApplicationContext());
                        startActivity(new Intent(this, AuthActivity.class));
                        finish();
                        Toast.makeText(this, "계정과 사용자 정보가 삭제되었어요.", Toast.LENGTH_LONG).show();
                    } else if (task.getException() instanceof FirebaseAuthRecentLoginRequiredException) {
                        new AlertDialog.Builder(this).setTitle("다시 로그인이 필요해요")
                            .setMessage("계정 보호를 위해 다시 로그인한 다음 마이페이지에서 계정 삭제를 눌러주세요.")
                            .setNegativeButton("취소", null)
                            .setPositiveButton("다시 로그인", (d, w) -> {
                                auth.signOut();
                                clearProviderSession(getApplicationContext());
                                startActivity(new Intent(this, AuthActivity.class));
                                finish();
                            }).show();
                    } else status.setText(error(task.getException()));
                });
            }).show();
    }

    private void signInGoogle() {
        if (busy) return;
        int clientId = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
        if (clientId == 0) {
            status.setText("Google 로그인 설정이 아직 완료되지 않았어요. 이메일 로그인을 이용해주세요.");
            return;
        }
        setBusy(true);
        pendingGoogle = new CancellationSignal();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
            .addCredentialOption(new GetSignInWithGoogleOption.Builder(getString(clientId)).build()).build();
        credentials.getCredentialAsync(this, request, pendingGoogle, this::runOnUiThread,
            new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                @Override public void onResult(GetCredentialResponse response) {
                    if (isDestroyed() || isFinishing()) return;
                    try {
                        Credential credential = response.getCredential();
                        if (!(credential instanceof CustomCredential)
                            || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            throw new IllegalArgumentException("Unsupported credential");
                        }
                        String token = GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken();
                        AuthCredential google = GoogleAuthProvider.getCredential(token, null);
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null && user.isAnonymous()) {
                            user.linkWithCredential(google).addOnCompleteListener(AuthActivity.this, linked -> {
                                if (!linked.isSuccessful() && linked.getException() instanceof FirebaseAuthUserCollisionException) {
                                    completeSignIn(auth.signInWithCredential(google));
                                } else completeSignIn(linked);
                            });
                        } else completeSignIn(auth.signInWithCredential(google));
                    } catch (Exception exception) {
                        setBusy(false);
                        status.setText("Google 계정을 확인하지 못했어요. 다시 시도해주세요.");
                    }
                }
                @Override public void onError(GetCredentialException exception) {
                    if (isDestroyed() || isFinishing()) return;
                    setBusy(false);
                    status.setText(exception instanceof GetCredentialCancellationException ? "로그인을 취소했어요."
                        : "Google 로그인에 실패했어요. 네트워크와 기기의 Google 계정을 확인해주세요.");
                }
            });
    }

    private void signOut() {
        if (busy) return;
        busy = true;
        if (pendingGoogle != null) pendingGoogle.cancel();
        // Local Firebase logout must not depend on a Google Play services callback.
        auth.signOut();
        clearProviderSession(getApplicationContext());
        Toast.makeText(this, "로그아웃되었어요.", Toast.LENGTH_SHORT).show();
        Intent calendar = new Intent(this, MainActivity.class);
        calendar.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(calendar);
        finish();
    }

    private static void clearProviderSession(android.content.Context context) {
        // Cleanup never holds the screen open or signs out a subsequent login.
        try {
            CredentialManager.create(context).clearCredentialStateAsync(
                new ClearCredentialStateRequest(), null,
                command -> new android.os.Handler(android.os.Looper.getMainLooper()).post(command),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override public void onResult(Void result) { }
                    @Override public void onError(ClearCredentialException error) {
                        android.util.Log.w("CalendarAuth", "Provider session cleanup unavailable");
                    }
                });
        } catch (RuntimeException exception) {
            android.util.Log.w("CalendarAuth", "Provider session cleanup could not start");
        }
    }

    private String error(Exception exception) {
        if (exception instanceof com.google.firebase.FirebaseNetworkException) return "인터넷 연결을 확인해주세요.";
        if (exception instanceof com.google.firebase.FirebaseTooManyRequestsException) return "요청이 많아요. 잠시 후 다시 시도해주세요.";
        if (exception instanceof FirebaseAuthException) {
            switch (((FirebaseAuthException) exception).getErrorCode()) {
                case "ERROR_EMAIL_ALREADY_IN_USE": case "ERROR_CREDENTIAL_ALREADY_IN_USE":
                    return "이미 사용 중인 계정이에요. 로그인 또는 비밀번호 재설정을 이용해주세요.";
                case "ERROR_WEAK_PASSWORD": return "더 안전한 비밀번호를 입력해주세요.";
                case "ERROR_OPERATION_NOT_ALLOWED": return "이 로그인 방식은 아직 준비 중이에요.";
                case "ERROR_USER_DISABLED": return "사용이 중지된 계정이에요.";
                case "ERROR_INVALID_CREDENTIAL": case "ERROR_WRONG_PASSWORD": case "ERROR_USER_NOT_FOUND":
                    return "이메일 또는 비밀번호를 확인해주세요.";
            }
        }
        return "처리하지 못했어요. 입력 내용과 연결 상태를 확인해주세요.";
    }

    private String verificationResult(String recipient, boolean accepted, Exception exception) {
        if (accepted) {
            android.util.Log.i("CalendarAuth", "verification_request_accepted");
            return "받는 주소: " + recipient
                + "\nFirebase가 인증 메일 발송 요청을 접수했어요. 실제 수신 여부는 확인할 수 없어요."
                + "\n스팸함·전체 메일함에서 ‘Verify your email’ 또는 ‘project-771b5’를 검색해주세요.";
        }
        String code = exception instanceof FirebaseAuthException
            ? ((FirebaseAuthException) exception).getErrorCode()
            : exception == null ? "UNKNOWN" : exception.getClass().getSimpleName();
        // Do not log email addresses, tokens or raw server exception messages.
        android.util.Log.w("CalendarAuth", "verification_request_failed: " + code);
        return "인증 메일 요청에 실패했어요.\n" + error(exception) + "\n오류 코드: " + code;
    }

    private void setBusy(boolean value) {
        busy = value;
        for (View control : controls) control.setEnabled(!value);
        status.setText(value ? "처리 중이에요…" : "");
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(getColor(R.color.text_primary));
        view.setPadding(0, dp(12), 0, dp(12));
        content.addView(view);
        return view;
    }

    private EditText field(String name, int type) {
        TextView label = label(name, 16);
        EditText view = new EditText(this);
        view.setId(View.generateViewId());
        label.setLabelFor(view.getId());
        view.setContentDescription(name);
        view.setInputType(type);
        view.setSingleLine(true);
        view.setTextSize(16);
        view.setMinHeight(dp(56));
        view.setSaveEnabled(false);
        content.addView(view, new LinearLayout.LayoutParams(-1, -2));
        controls.add(view);
        return view;
    }

    private void button(String title, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setMinHeight(dp(52));
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setTextColor(getColor(primary ? R.color.surface_primary : R.color.text_primary));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(getColor(primary ? R.color.charcoal : R.color.surface_primary));
        shape.setCornerRadius(dp(24));
        button.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        content.addView(button, params);
        controls.add(button);
        button.setOnClickListener(v -> action.run());
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        if (pendingGoogle != null) pendingGoogle.cancel();
        super.onDestroy();
    }
}
