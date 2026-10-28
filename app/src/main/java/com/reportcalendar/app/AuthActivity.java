package com.reportcalendar.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
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
    private boolean profileEditing;
    private String profileNameDraft = "", profilePhotoDraft = "", profileUid = "", photoPickerUid = "";
    private EditText profileName;
    private ImageView profilePreview;
    private static final int AVATAR_REQUEST = 4902;
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
        if (saved != null) {
            profileEditing = saved.getBoolean("profileEditing");
            profileNameDraft = saved.getString("profileName", "");
            profilePhotoDraft = saved.getString("profilePhoto", "");
            profileUid = saved.getString("profileUid", "");
            photoPickerUid = saved.getString("photoPickerUid", "");
        }
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
        rememberProfileDraft();
        out.putBoolean("profileEditing", profileEditing);
        out.putString("profileName", profileNameDraft);
        out.putString("profilePhoto", profilePhotoDraft);
        out.putString("profileUid", profileUid);
        out.putString("photoPickerUid", photoPickerUid);
        // No password, Google token or reauthentication credential is serialized.
    }
    private void syncUi() {
        if (!resumed || isDestroyed() || isFinishing() || navigating) return;
        FirebaseUser user = flow.auth.getCurrentUser();
        // Email verification gates profile writes, not access to My Page.
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
        String notice = flow.pendingDeletionUid().isEmpty() ? "" : "\n계정 삭제가 끝났는지 확인해 주세요.";
        status.setText(flow.state.message + notice);
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }
    private void render() {
        rememberProfileDraft();
        controls.clear();
        email = password = confirm = null;
        profileName = null;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        int systemFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (android.os.Build.VERSION.SDK_INT >= 26) systemFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        else getWindow().setNavigationBarColor(getColor(R.color.charcoal));
        getWindow().getDecorView().setSystemUiVisibility(systemFlags);
        LinearLayout canvas = new LinearLayout(this);
        canvas.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(12), dp(24), dp(32));
        int width = Math.min(getResources().getDisplayMetrics().widthPixels, dp(560));
        canvas.addView(content, new LinearLayout.LayoutParams(width, -2));
        scroll.addView(canvas);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            ViewGroup.LayoutParams layout = content.getLayoutParams();
            layout.width = Math.min(dp(560), getResources().getDisplayMetrics().widthPixels
                - insets.getSystemWindowInsetLeft() - insets.getSystemWindowInsetRight());
            content.setLayoutParams(layout);
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_chevron_left);
        back.setBackground(shape(Color.WHITE, 24, 0));
        back.setContentDescription("캘린더로 돌아가기");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView appTitle = new TextView(this);
        appTitle.setText("디데이 캘린더"); appTitle.setTextSize(14);
        appTitle.setTextColor(getColor(R.color.account_muted));
        toolbar.addView(appTitle);
        content.addView(toolbar);
        FirebaseUser user = flow.auth.getCurrentUser();
        if (isMyPage() && user != null && !user.isAnonymous()) {
            if (!user.getUid().equals(profileUid)) { profileEditing = false; profileUid = user.getUid(); }
            heading("마이페이지", "내 정보와 계정을 한곳에서 관리해요.");
            LinearLayout hero = new LinearLayout(this);
            hero.setOrientation(LinearLayout.VERTICAL); hero.setGravity(Gravity.CENTER_HORIZONTAL);
            hero.setPadding(dp(20), dp(24), dp(20), dp(20));
            hero.setBackground(shape(getColor(R.color.account_soft), 24, 0));
            ImageView avatar = avatarView();
            ProfilePhotos.show(this, avatar, ProfilePhotos.get(this, user.getUid()));
            hero.addView(avatar, new LinearLayout.LayoutParams(dp(88), dp(88)));
            TextView name = new TextView(this);
            name.setText(user.getDisplayName() == null || user.getDisplayName().trim().isEmpty() ? "이름을 설정해 주세요" : user.getDisplayName());
            name.setTextSize(22); name.setTypeface(null, Typeface.BOLD); name.setGravity(Gravity.CENTER);
            name.setTextColor(getColor(R.color.text_primary)); name.setPadding(0,dp(14),0,dp(6)); hero.addView(name);
            TextView address = new TextView(this); address.setText(user.getEmail()); address.setTextSize(14);
            address.setTextColor(getColor(R.color.account_muted)); address.setGravity(Gravity.CENTER); hero.addView(address);
            TextView badge = new TextView(this);
            badge.setText(user.isEmailVerified() ? "이메일 인증 완료" : "이메일 인증 대기");
            badge.setTextColor(getColor(R.color.account_accent)); badge.setTextSize(13);
            badge.setPadding(dp(12),dp(8),dp(12),dp(8)); badge.setBackground(shape(Color.WHITE,20,0));
            LinearLayout.LayoutParams badgeParams=new LinearLayout.LayoutParams(-2,-2); badgeParams.topMargin=dp(14);
            hero.addView(badge,badgeParams); content.addView(hero);
            button("프로필 편집", false, () -> {
                if (!profileEditing) {
                    profileNameDraft = user.getDisplayName() == null ? "" : user.getDisplayName();
                    profilePhotoDraft = ProfilePhotos.get(this, user.getUid()); profileEditing = true;
                } else profileEditing = false;
                render(); syncUi();
            });
            if (profileEditing) buildProfileEditor(user);
            status = notice();
            label("계정 보안", 14).setTextColor(getColor(R.color.account_accent));
            if (!user.isEmailVerified()) {
                menuRow("인증 메일 다시 보내기", "메일함에서 인증 링크를 확인해 주세요", R.drawable.ic_notifications, flow::sendVerification, false);
            }
            menuRow("인증 완료 확인", "메일에서 인증한 뒤 눌러 주세요", R.drawable.ic_person, flow::checkVerification, false);
            if (hasProvider(user, "password")) menuRow("비밀번호 재설정", "이메일로 재설정 링크 받기", R.drawable.ic_assignment, () -> flow.resetPassword(user.getEmail()), false);
            else label("Google 계정의 비밀번호는 Google 계정 설정에서 변경할 수 있어요.", 14);
            label("계정 관리", 14).setTextColor(getColor(R.color.account_accent));
            menuRow("로그아웃", "이 기기의 수행평가는 유지돼요", R.drawable.ic_chevron_right, () -> {
                dialog = new AlertDialog.Builder(this).setMessage("로그아웃할까요? 이 기기의 수행평가는 삭제되지 않습니다.")
                    .setNegativeButton("취소", null).setPositiveButton("로그아웃", (d, w) -> flow.signOut()).show();
            }, false);
            menuRow("계정 삭제", "본인 확인 후 계정 삭제", R.drawable.ic_close, () -> confirmAccountDeletion(user), true);
            if (!flow.pendingDeletionUid().isEmpty()) button("계정 삭제 상태 확인", false, flow::reconcileDeletion);
        } else {
            TextView mark = label("D", 24); mark.setTypeface(null,Typeface.BOLD);
            mark.setGravity(Gravity.CENTER); mark.setTextColor(getColor(R.color.account_accent));
            mark.setBackground(shape(getColor(R.color.account_soft),18,0));
            LinearLayout.LayoutParams markParams=new LinearLayout.LayoutParams(dp(56),dp(56)); markParams.topMargin=dp(24); markParams.bottomMargin=dp(16); mark.setLayoutParams(markParams);
            heading(registering ? "계정 만들기" : "로그인", registering ? "내 일정 관리를 시작해 보세요." : "반가워요. 내 일정 관리를 이어가세요.");
            email = field("이메일", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            password = field("비밀번호", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            if (registering) confirm = field("비밀번호 확인", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            status = notice();
            button(registering ? "회원가입" : "이메일로 로그인", true, this::submitEmail);
            if (!registering) {
                TextView separator=label("또는",14); separator.setGravity(Gravity.CENTER); separator.setTextColor(getColor(R.color.account_muted));
                button("Google 계정으로 로그인", false, () -> signInGoogle(false));
            }
            button(registering ? "이미 계정이 있어요 · 로그인" : "이메일로 회원가입", false, () -> {
                String address = email.getText().toString();
                registering = !registering; render(); email.setText(address); syncUi();
            });
            button("비밀번호 재설정", false, () -> {
                if (validEmail()) flow.resetPassword(email.getText().toString().trim());
            });
            if (!flow.pendingDeletionUid().isEmpty()) button("계정 삭제 상태 확인", false, flow::reconcileDeletion);
            label("다른 사람이 공개한 과제는 메뉴의 ‘공개 과제 가져오기’에서 볼 수 있어요.", 14);
        }
        setContentView(scroll);
    }

    private void rememberProfileDraft() {
        if (profileEditing && profileName != null) profileNameDraft = profileName.getText().toString();
    }
    private void buildProfileEditor(FirebaseUser user) {
        label("프로필 설정",20).setTypeface(null,Typeface.BOLD);
        profilePreview = avatarView();
        ProfilePhotos.show(this,profilePreview,profilePhotoDraft);
        LinearLayout.LayoutParams photoParams=new LinearLayout.LayoutParams(dp(80),dp(80)); photoParams.gravity=Gravity.CENTER_HORIZONTAL;
        content.addView(profilePreview,photoParams);
        profileName = field("이름",InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        profileName.setText(profileNameDraft);
        profileName.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
        button("프로필 사진 선택",false,() -> {
            rememberProfileDraft(); photoPickerUid=user.getUid();
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
            pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try {startActivityForResult(pick,AVATAR_REQUEST);} catch (android.content.ActivityNotFoundException e) {status.setText("사진 선택 앱을 찾을 수 없어요.");}
        });
        button("기본 사진으로 변경",false,() -> {profilePhotoDraft=""; ProfilePhotos.show(this,profilePreview,"");});
        label("사진은 이 휴대폰에서만 보여요. 바꾼 내용을 적용하려면 ‘프로필 저장’을 눌러 주세요.",14).setTextColor(getColor(R.color.account_muted));
        button("프로필 저장",true,() -> {
            rememberProfileDraft();
            if (profileNameDraft.trim().isEmpty()) {profileName.setError("이름을 입력해 주세요"); return;}
            flow.updateProfile(profileNameDraft,profilePhotoDraft,profileUid);
        });
        button("편집 취소",false,() -> {profileEditing=false; render(); syncUi();});
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request != AVATAR_REQUEST || result != RESULT_OK || data == null || data.getData() == null) return;
        FirebaseUser user=flow.auth.getCurrentUser();
        if(user == null || !user.getUid().equals(photoPickerUid) || !profileEditing) return;
        Uri uri=data.getData();
        if(!"content".equals(uri.getScheme())) {status.setText("기기에서 사진을 선택해 주세요."); return;}
        try {
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            profilePhotoDraft=uri.toString();
            ProfilePhotos.show(this,profilePreview,profilePhotoDraft);
        } catch (SecurityException e) {status.setText("이 사진은 저장할 수 없어요. 다른 사진을 선택해 주세요.");}
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
            .setMessage("본인 확인 후 계정과 내 정보를 삭제해요. 휴대폰에 저장한 수행평가는 남아요. 인터넷에 연결해 주세요.")
            .setNegativeButton("취소", null).setPositiveButton("본인 인증 후 삭제", (d, w) -> {
                if (hasProvider(user, "password") && hasProvider(user, "google.com")) {
                    dialog = new AlertDialog.Builder(this).setTitle("본인 확인 방법")
                        .setItems(new String[]{"비밀번호 확인", "Google 계정 확인"}, (choice, index) -> {
                            if (index == 0) askPassword(); else signInGoogle(true);
                        }).setNegativeButton("취소", null).show();
                } else if (hasProvider(user, "password")) askPassword();
                else if (hasProvider(user, "google.com")) signInGoogle(true);
                else status.setText("이 계정의 본인 확인 방법은 지원되지 않아요. 삭제를 시작하지 않았어요.");
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
        if (client == 0) { status.setText("지금은 Google로 로그인할 수 없어요. 이메일 로그인을 이용해 주세요."); return; }
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
    private GradientDrawable shape(int color, int radius, int border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radius));
        if (border != 0) drawable.setStroke(dp(1), border);
        return drawable;
    }
    private void heading(String title, String subtitle) {
        TextView heading = label(title, 28); heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, dp(20), 0, dp(4));
        TextView caption = label(subtitle, 15);
        caption.setTextColor(getColor(R.color.account_muted));
        caption.setPadding(0, dp(4), 0, dp(24));
    }
    private TextView notice() {
        TextView view = label("", 14);
        view.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        view.setBackground(shape(getColor(R.color.account_soft), 14, 0));
        LinearLayout.LayoutParams noticeParams = new LinearLayout.LayoutParams(-1, -2);
        noticeParams.topMargin = dp(16); noticeParams.bottomMargin = dp(8);
        view.setLayoutParams(noticeParams);
        view.setVisibility(View.GONE);
        // Direct validation and picker errors also reveal the notice, without a controller event.
        view.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                view.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
            }
            public void afterTextChanged(android.text.Editable e) { }
        });
        return view;
    }
    private ImageView avatarView() {
        ImageView avatar = new ImageView(this);
        avatar.setContentDescription("프로필 사진");
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(shape(Color.WHITE, 100, 0));
        avatar.setClipToOutline(true);
        return avatar;
    }
    private void menuRow(String title, String subtitle, int icon, Runnable action, boolean danger) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL); row.setMinimumHeight(dp(72));
        row.setPadding(dp(4), dp(12), dp(4), dp(12));
        row.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(getColor(R.color.account_soft)), shape(Color.WHITE, 12, 0), null));
        ImageView symbol = new ImageView(this); symbol.setImageResource(icon);
        symbol.setColorFilter(getColor(danger ? R.color.danger : R.color.account_accent));
        row.addView(symbol, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this); name.setText(title); name.setTextSize(16);
        name.setTextColor(getColor(danger ? R.color.danger : R.color.text_primary));
        TextView description = new TextView(this); description.setText(subtitle); description.setTextSize(13);
        description.setTextColor(getColor(R.color.account_muted)); description.setPadding(0,dp(4),0,0);
        words.addView(name); words.addView(description);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1); params.leftMargin = dp(16);
        row.addView(words, params);
        ImageView arrow = new ImageView(this); arrow.setImageResource(R.drawable.ic_chevron_right);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(20),dp(20)));
        row.setFocusable(true); row.setContentDescription(title + ", " + subtitle);
        symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        words.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.setOnClickListener(v -> action.run()); controls.add(row); content.addView(row);
        View divider = new View(this); divider.setBackgroundColor(getColor(R.color.outline));
        content.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
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
        view.setTextColor(getColor(R.color.text_primary));
        view.setPadding(dp(16),dp(14),dp(16),dp(14));
        StateListDrawable border = new StateListDrawable();
        border.addState(new int[]{android.R.attr.state_focused}, shape(Color.WHITE,12,getColor(R.color.account_blue)));
        border.addState(new int[]{}, shape(Color.WHITE,12,getColor(R.color.outline)));
        view.setBackground(border);
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
        shape.setColor(getColor(primary ? R.color.account_blue : R.color.surface_primary));
        shape.setCornerRadius(dp(14));
        if (!primary) shape.setStroke(dp(1),getColor(R.color.outline));
        button.setStateListAnimator(null);
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
