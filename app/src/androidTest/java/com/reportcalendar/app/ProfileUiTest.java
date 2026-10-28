package com.reportcalendar.app;

import android.content.Context;
import android.os.Build;
import android.widget.*;
import android.view.*;
import android.graphics.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.FirebaseFirestore;
import java.lang.reflect.*;
import java.util.concurrent.TimeUnit;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Local Authentication Emulator only. Never uses a real account or live Firestore. */
@RunWith(AndroidJUnit4.class)
public class ProfileUiTest {
    private Context context;
    private FirebaseAuth auth;
    @Before public void setup() throws Exception {
        assertTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"));
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        FirebaseApp.initializeApp(context);
        auth=FirebaseAuth.getInstance();
        auth.useEmulator("127.0.0.1",9099);
        FirebaseFirestore.getInstance().useEmulator("127.0.0.1",1);
        auth.signOut();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            AuthController flow=AuthController.get(context);
            flow.state.message=""; flow.state.busy=false; flow.state.kind="";
        });
        Tasks.await(auth.createUserWithEmailAndPassword("qa-"+System.nanoTime()+"@example.test","qa-only-password"),10,TimeUnit.SECONDS);
    }
    @After public void cleanup() throws Exception {
        if(auth.getCurrentUser()!=null) Tasks.await(auth.getCurrentUser().delete(),10,TimeUnit.SECONDS);
        auth.signOut();
    }
    private Object field(Object a,String name) throws Exception {
        Field f=AuthActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(a);
    }
    private View find(View v,String title) {
        if(v instanceof TextView && title.contentEquals(((TextView)v).getText())) return v;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) {
            View result=find(((ViewGroup)v).getChildAt(i),title); if(result!=null)return result;
        }
        return null;
    }
    private void tap(AuthActivity a,String text) {assertNotNull(find(a.getWindow().getDecorView(),text));find(a.getWindow().getDecorView(),text).performClick();}
    private void capture(AuthActivity a,String name) {
        // Draw a synthetic test-account screen; production windows retain FLAG_SECURE.
        View view=a.getWindow().getDecorView();
        Bitmap image=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name+".png"))) { image.compress(Bitmap.CompressFormat.PNG,100,out); }
        catch(IOException e){throw new AssertionError(e);}
        image.recycle();
    }
    @Test public void profileDraftSurvivesRotationAndNameSaves() throws Exception {
        try(ActivityScenario<MyPageActivity> screen=ActivityScenario.launch(MyPageActivity.class)) {
            screen.onActivity(a->{capture(a,"qa-mypage");tap(a,"프로필 편집");try{((EditText)field(a,"profileName")).setText("테스트 사용자");}catch(Exception e){throw new AssertionError(e);}});
            screen.recreate();
            screen.onActivity(a->{try{assertEquals("테스트 사용자",((EditText)field(a,"profileName")).getText().toString());tap(a,"프로필 저장");}catch(Exception e){throw new AssertionError(e);}});
            for(int i=0;i<100 && !"테스트 사용자".equals(auth.getCurrentUser().getDisplayName());i++) Thread.sleep(100);
            assertEquals("테스트 사용자",auth.getCurrentUser().getDisplayName());
            assertFalse(auth.getCurrentUser().isEmailVerified());
            for(int i=0;i<100 && AuthController.get(context).state.busy;i++) Thread.sleep(100);
            assertFalse(AuthController.get(context).state.busy);
            screen.onActivity(a->tap(a,"편집 취소"));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            Thread.sleep(300);
            screen.onActivity(a->capture(a,"qa-mypage-saved"));
        }
    }
    @Test public void avatarSettingsAreUidScopedAndCancelDoesNotSave() {
        String uid=auth.getCurrentUser().getUid();
        assertTrue(ProfilePhotos.save(context,uid,"content://qa/avatar"));
        assertEquals("",ProfilePhotos.get(context,"another-qa-account"));
        try(ActivityScenario<MyPageActivity> screen=ActivityScenario.launch(MyPageActivity.class)) {
            screen.onActivity(a->{tap(a,"프로필 편집");tap(a,"기본 사진으로 변경");tap(a,"편집 취소");});
            assertEquals("content://qa/avatar",ProfilePhotos.get(context,uid));
        }
        ProfilePhotos.save(context,uid,"");
    }
    @Test public void loginLayoutHasLabelsAndActions() throws Exception {
        Tasks.await(auth.getCurrentUser().delete(),10,TimeUnit.SECONDS);auth.signOut();
        try(ActivityScenario<AuthActivity> screen=ActivityScenario.launch(AuthActivity.class)) {
            screen.onActivity(a->{assertNotNull(find(a.getWindow().getDecorView(),"이메일로 로그인"));assertNotNull(find(a.getWindow().getDecorView(),"Google 계정으로 로그인"));capture(a,"qa-login");});
        }
    }
}
