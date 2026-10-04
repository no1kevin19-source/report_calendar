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
import java.util.ArrayList;

/** Screen only: process-owned AuthController survives rotation, back navigation and onStop. */
public class AuthActivity extends Activity {
    protected boolean isMyPage() { return false; }
    private AuthController flow;
    private LinearLayout content;
    private EditText email, password, confirm;
    private TextView status;
    private boolean registering, resumed, navigating;
    private long renderedResult = -1, googleRequest;
    private CancellationSignal picker;
    private AlertDialog dialog;
    private final ArrayList<View> controls = new ArrayList<>();
    private final AuthController.Observer observer = this::syncUi;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        flow = AuthController.get(this);
        registering = saved != null && saved.getBoolean("registering");
        if (flow.state.busy && "register".equals(flow.state.kind)) registering = true;
        if (flow.state.accountCreated && "register".equals(flow.state.kind)) registering = false;
        if (saved != null) renderedResult = saved.getLong("renderedResult", -1);
        render();
        if (email != null) email.setText(saved != null ? saved.getString("email", flow.state.email) : flow.state.email);
    }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        flow.observe(observer);
        flow.foreground();
    }
    @Override protected void onPause() {
        resumed = false;
        flow.remove(observer);
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("registering", registering);
        out.putLong("renderedResult", renderedResult);
        if (email != null) out.putString("email", email.getText().toString());
        // No password, Google token or reauthentication credential is serialized.
    }
    private void syncUi() {
        if (!resumed || isDestroyed() || isFinishing() || navigating) return;
        FirebaseUser user = flow.auth.getCurrentUser();
        boolean signedIn = user != null && !user.isAnonymous();
        if (!flow.state.busy && isMyPage() != signedIn) {
            navigating = true;
            startActivity(new Intent(this, signedIn ? MyPageActivity.class : AuthActivity.class));
            finish();
            return;
        }
        if (!flow.state.busy && renderedResult != flow.state.id) {
            renderedResult = flow.state.id;
            if (isMyPage()) render();
            else if ("register".equals(flow.state.kind) && flow.state.accountCreated) {
                registering = false; render();
                email.setText(flow.state.email);
            }
        }
        for (View control : controls) control.setEnabled(!flow.state.busy);
        String notice = flow.pendingDeletionUid().isEmpty() ? "" : "\n미완료된 계정 삭제 기록이 있어요. 아래에서 처리 결과를 확인해주세요.";
        status.setText(flow.state.message + notice);
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
        controls.remove(controls.size() - 1); // Leaving the screen never cancels Firebase work.
        FirebaseUser user = flow.auth.getCurrentUser();
        if (isMyPage() && user != null && !user.isAnonymous()) {
            label("마이페이지", 28);
            label(user.getEmail() == null ? "로그인됨" : user.getEmail(), 18);
            label("이 기기에 저장된 수행평가는 그대로 유지됩니다.", 16);
            status = label("", 16);
            status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            label(user.isEmailVerified() ? "이메일 인증 완료" : "이메일 미인증 · 메일함과 스팸함을 확인해주세요.", 16);
            if (!user.isEmailVerified()) {
                button("인증 메일 다시 보내기", false, flow::sendVerification);
                button("인증 완료 확인", false, flow::checkVerification);
            }
            if (hasProvider(user, "password")) button("비밀번호 재설정", false, () -> flow.resetPassword(user.getEmail()));
            else label("Google 계정의 비밀번호는 Google 계정 설정에서 변경할 수 있어요.", 14);
            button("계정 삭제", false, () -> confirmAccountDeletion(user));
            if (!flow.pendingDeletionUid().isEmpty()) button("삭제 처리 결과 확인", false, flow::reconcileDeletion);
            button("로그아웃", true, () -> {
                dialog = new AlertDialog.Builder(this).setMessage("로그아웃할까요? 이 기기의 수행평가는 삭제되지 않습니다.")
                    .setNegativeButton("취소", null).setPositiveButton("로그아웃", (d, w) -> flow.signOut()).show();
            });
        } else {
            label(registering ? "이메일 회원가입" : "로그인", 28);
            label("이메일과 비밀번호 또는 Google 계정으로 시작하세요.", 16);
            email = field("이메일", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            password = field("비밀번호", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            if (registering) confirm = field("비밀번호 확인", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            status = label("", 16);
            status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            button(registering ? "회원가입" : "이메일로 로그인", true, this::submitEmail);
            if (!registering) button("Google 계정으로 로그인", false, () -> signInGoogle(false));
            button(registering ? "이미 계정이 있어요 · 로그인" : "이메일로 회원가입", false, () -> {
                String address = email.getText().toString();
                registering = !registering; render(); email.setText(address); syncUi();
            });
            button("비밀번호 재설정", false, () -> {
                if (validEmail()) flow.resetPassword(email.getText().toString().trim());
            });
            if (!flow.pendingDeletionUid().isEmpty()) button("삭제 처리 결과 확인", false, flow::reconcileDeletion);
            label("로그인만으로 수행평가가 자동으로 동기화되지는 않아요.", 14);
        }
        setContentView(scroll);
    }
    private boolean validEmail() {
        if (!Patterns.EMAIL_ADDRESS.matcher(email.getText().toString().trim()).matches()) {
            email.setError("올바른 이메일 주소를 입력해주세요."); email.requestFocus(); return false;
        }
        return true;
    }
    private void submitEmail() {
        if (flow.state.busy || !validEmail()) return;
        String address = email.getText().toString().trim();
        String secret = password.getText().toString();
        if (secret.isEmpty() || (registering && secret.length() < 6)) {
            password.setError(registering ? "비밀번호를 6자 이상 입력해주세요." : "비밀번호를 입력해주세요."); return;
        }
        if (registering && !secret.equals(confirm.getText().toString())) {
            confirm.setError("비밀번호가 일치하지 않아요."); return;
        }
        password.setText("");
        if (confirm != null) confirm.setText("");
        if (registering) flow.register(address, secret);
        else flow.signIn(address, secret);
    }
    private boolean hasProvider(FirebaseUser user, String name) {
        for (UserInfo provider : user.getProviderData()) if (name.equals(provider.getProviderId())) return true;
        return false;
    }
    private void confirmAccountDeletion(FirebaseUser user) {
        if (flow.state.busy) return;
        dialog = new AlertDialog.Builder(this).setTitle("계정을 삭제할까요?")
            .setMessage("먼저 본인 인증을 진행합니다. 로그인 계정과 사용자 정보를 삭제하며 이 기기의 수행평가는 유지됩니다. 인터넷 연결이 필요해요.")
            .setNegativeButton("취소", null).setPositiveButton("본인 인증 후 삭제", (d, w) -> {
                if (hasProvider(user, "password") && hasProvider(user, "google.com")) {
                    dialog = new AlertDialog.Builder(this).setTitle("재인증 방식")
                        .setItems(new String[]{"비밀번호 확인", "Google 계정 확인"}, (choice, index) -> {
                            if (index == 0) askPassword(); else signInGoogle(true);
                        }).setNegativeButton("취소", null).show();
                } else if (hasProvider(user, "password")) askPassword();
                else if (hasProvider(user, "google.com")) signInGoogle(true);
                else status.setText("이 계정의 재인증 방식은 지원되지 않아요. 삭제를 시작하지 않았어요.");
            }).show();
    }
    private void askPassword() {
        EditText secret = new EditText(this);
        secret.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        secret.setSaveEnabled(false);
        secret.setHint("현재 비밀번호"); secret.setContentDescription("계정 삭제 확인용 현재 비밀번호");
        secret.setPadding(dp(24), dp(16), dp(24), dp(16));
        dialog = new AlertDialog.Builder(this).setTitle("비밀번호 확인").setView(secret)
            .setNegativeButton("취소", (d, w) -> secret.setText(""))
            .setPositiveButton("인증하고 삭제", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = secret.getText().toString();
            if (value.isEmpty()) { secret.setError("비밀번호를 입력해주세요."); return; }
            secret.setText(""); dialog.dismiss(); flow.deleteWithPassword(value);
        }));
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
    }
    private void signInGoogle(boolean deleting) {
        if (flow.state.busy) return;
        int client = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
        if (client == 0) { status.setText("Google 로그인 설정이 아직 완료되지 않았어요."); return; }
        googleRequest = flow.beginGoogle(deleting);
        if (googleRequest == 0) return;
        picker = new CancellationSignal();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
            .addCredentialOption(new GetSignInWithGoogleOption.Builder(getString(client)).build()).build();
        try {
            CredentialManager.create(getApplicationContext()).getCredentialAsync(this, request, picker,
                flow.executor(), new GoogleReply(flow, googleRequest));
        } catch (RuntimeException error) { flow.googleFailure(googleRequest, false); }
    }
    /** Never captures an Activity; late picker results are rejected by request ID and phase. */
    private static final class GoogleReply implements CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
        private final AuthController flow;
        private final long id;
        GoogleReply(AuthController flow, long id) { this.flow = flow; this.id = id; }
        public void onResult(GetCredentialResponse response) {
            try {
                Credential credential = response.getCredential();
                if (!(credential instanceof CustomCredential)
                    || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType()))
                    throw new IllegalArgumentException("Unsupported credential");
                flow.googleCredential(id, GoogleAuthProvider.getCredential(
                    GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken(), null));
            } catch (Exception error) { flow.googleFailure(id, false); }
        }
        public void onError(GetCredentialException error) {
            flow.googleFailure(id, error instanceof GetCredentialCancellationException);
        }
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
        flow.remove(observer);
        if (dialog != null) dialog.dismiss();
        if (picker != null && flow.isPicker(googleRequest)) {
            flow.cancelPicker(googleRequest); picker.cancel();
        }
        super.onDestroy();
    }
}
