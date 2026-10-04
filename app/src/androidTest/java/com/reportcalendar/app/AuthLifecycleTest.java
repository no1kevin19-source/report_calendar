package com.reportcalendar.app;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.FirebaseApp;
import org.junit.*;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

/** Offline UI tests: injected operation state, NOT claims about real Firebase requests. */
@RunWith(AndroidJUnit4.class)
public class AuthLifecycleTest {
    private Context context;
    private AuthController flow;
    private Map<String, ?> calendarBefore;
    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Refuse to run on a network-connected device or with unfinished user deletion data.
        assertEquals("Tests require an offline disposable emulator", 1,
            Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0));
        assertEquals(0, Settings.Global.getInt(context.getContentResolver(), Settings.Global.WIFI_ON, 0));
        Assume.assumeTrue(DeletionJournal.pendingUid(context).isEmpty());
        calendarBefore = new HashMap<>(context.getSharedPreferences("report_calendar_native", 0).getAll());
        ui(() -> {
            FirebaseApp.initializeApp(context);
            flow = AuthController.get(context);
            flow.auth.signOut();
            flow.state.busy = false;
            flow.state.kind = "";
            flow.state.message = "";
            flow.state.email = "";
            flow.state.accountCreated = false;
        });
    }
    @After public void unchangedCalendarData() {
        assertEquals(calendarBefore, context.getSharedPreferences("report_calendar_native", 0).getAll());
    }
    private void ui(Runnable runnable) { InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable); }
    private void complete(long id, String message) {
        ui(() -> {
            try {
                Method finish = AuthController.class.getDeclaredMethod("finish", long.class, String.class);
                finish.setAccessible(true); finish.invoke(flow, id, message);
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }
    private View named(View view, String name) {
        if (name.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) {
            View found = named(((ViewGroup)view).getChildAt(i), name);
            if (found != null) return found;
        }
        return null;
    }
    private boolean contains(View view, String text) {
        if (view instanceof TextView && ((TextView)view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++)
            if (contains(((ViewGroup)view).getChildAt(i), text)) return true;
        return false;
    }
    @Test public void backgroundCompletionAppearsOnReturn() {
        long[] id = new long[1];
        ui(() -> id[0] = flow.state.begin("reset", "offline@example.invalid"));
        try (ActivityScenario<AuthActivity> screen = ActivityScenario.launch(AuthActivity.class)) {
            screen.moveToState(Lifecycle.State.CREATED);
            complete(id[0], "오프라인 테스트 완료 결과");
            screen.moveToState(Lifecycle.State.RESUMED);
            screen.onActivity(activity -> {
                assertFalse(flow.state.busy);
                assertTrue(contains(activity.getWindow().getDecorView(), "오프라인 테스트 완료 결과"));
            });
        }
    }
    @Test public void rotationKeepsRequestButNeverSavesPassword() {
        long[] id = new long[1];
        try (ActivityScenario<AuthActivity> screen = ActivityScenario.launch(AuthActivity.class)) {
            screen.onActivity(activity -> {
                ((EditText)named(activity.getWindow().getDecorView(), "이메일")).setText("offline@example.invalid");
                ((EditText)named(activity.getWindow().getDecorView(), "비밀번호")).setText("memory-only-secret");
                id[0] = flow.state.begin("login", "offline@example.invalid");
            });
            screen.recreate();
            screen.onActivity(activity -> {
                assertTrue(flow.state.accepts(id[0]));
                assertEquals("offline@example.invalid", ((EditText)named(activity.getWindow().getDecorView(), "이메일")).getText().toString());
                assertEquals("", ((EditText)named(activity.getWindow().getDecorView(), "비밀번호")).getText().toString());
                assertEquals(0, flow.state.begin("register", "duplicate@example.invalid"));
            });
            complete(id[0], "로그인 오류 테스트");
            screen.onActivity(activity -> assertTrue(contains(activity.getWindow().getDecorView(), "로그인 오류 테스트")));
        }
    }
    @Test public void realTaskListenerSurvivesStoppedActivity() {
        com.google.android.gms.tasks.TaskCompletionSource<com.google.firebase.auth.AuthResult> request =
            new com.google.android.gms.tasks.TaskCompletionSource<>();
        ui(() -> {
            long id = flow.state.begin("login", "offline@example.invalid");
            try {
                Method method = AuthController.class.getDeclaredMethod("completeSignIn", long.class, com.google.android.gms.tasks.Task.class);
                method.setAccessible(true); method.invoke(flow, id, request.getTask());
            } catch (Exception e) { throw new AssertionError(e); }
        });
        try (ActivityScenario<AuthActivity> screen = ActivityScenario.launch(AuthActivity.class)) {
            screen.moveToState(Lifecycle.State.CREATED);
            request.setException(new com.google.firebase.FirebaseNetworkException("Synthetic offline test"));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertFalse(flow.state.busy);
            screen.moveToState(Lifecycle.State.RESUMED);
            screen.onActivity(activity -> assertTrue(contains(activity.getWindow().getDecorView(), "인터넷 연결을 확인")));
        }
    }
    @Test public void registrationBackReentrySharesGateAndShowsPartialSuccess() {
        long[] id = new long[1];
        ui(() -> id[0] = flow.state.begin("register", "offline@example.invalid"));
        try (ActivityScenario<AuthActivity> first = ActivityScenario.launch(AuthActivity.class)) {
            first.onActivity(Activity::finish);
        }
        assertTrue(flow.state.busy);
        try (ActivityScenario<AuthActivity> next = ActivityScenario.launch(AuthActivity.class)) {
            next.recreate();
            next.onActivity(activity -> assertEquals(0, flow.state.begin("register", "second@example.invalid")));
            ui(() -> flow.state.accountCreated = true);
            complete(id[0], "계정 생성 완료 · 메일 실패 테스트");
            next.onActivity(activity -> {
                assertTrue(contains(activity.getWindow().getDecorView(), "이메일로 로그인"));
                assertTrue(contains(activity.getWindow().getDecorView(), "메일 실패 테스트"));
                assertNull(flow.auth.getCurrentUser());
            });
        }
    }
    @Test public void destroyedScreenDoesNotConsumeVerificationResult() {
        long[] id = new long[1];
        ui(() -> id[0] = flow.state.begin("mail", ""));
        try (ActivityScenario<AuthActivity> first = ActivityScenario.launch(AuthActivity.class)) {
            first.onActivity(Activity::finish);
        }
        complete(id[0], "인증 메일 요청 결과 테스트");
        try (ActivityScenario<AuthActivity> next = ActivityScenario.launch(AuthActivity.class)) {
            next.onActivity(activity -> assertTrue(contains(activity.getWindow().getDecorView(), "인증 메일 요청 결과 테스트")));
        }
    }
    @Test public void rotationCancelsOnlyPickerAndIgnoresLateCallback() {
        long[] id = new long[1];
        try (ActivityScenario<AuthActivity> screen = ActivityScenario.launch(AuthActivity.class)) {
            screen.onActivity(activity -> {
                id[0] = flow.beginGoogle(false);
                try {
                    Field request = AuthActivity.class.getDeclaredField("googleRequest");
                    Field signal = AuthActivity.class.getDeclaredField("picker");
                    request.setAccessible(true); signal.setAccessible(true);
                    request.setLong(activity, id[0]); signal.set(activity, new CancellationSignal());
                } catch (Exception e) { throw new AssertionError(e); }
            });
            screen.recreate();
            screen.onActivity(activity -> {
                assertFalse(flow.state.busy);
                long newer = flow.state.begin("login", "");
                flow.googleFailure(id[0], false);
                assertTrue(flow.state.accepts(newer));
                flow.state.finish(newer, "done");
            });
        }
    }
    @Test public void mainDraftSurvivesAuthVisitAndRecreation() {
        try (ActivityScenario<MainActivity> main = ActivityScenario.launch(MainActivity.class)) {
            main.onActivity(activity -> draft(activity).setText("로컬 작성 중 초안 테스트"));
            android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            android.app.Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(AuthActivity.class.getName(), null, false);
            main.onActivity(activity -> activity.startActivity(new android.content.Intent(activity, AuthActivity.class)));
            Activity login = monitor.waitForActivityWithTimeout(5000);
            assertNotNull(login);
            ui(() -> { flow.signOut(); login.finish(); });
            instrumentation.waitForIdleSync();
            instrumentation.removeMonitor(monitor);
            main.recreate();
            main.onActivity(activity -> assertEquals("로컬 작성 중 초안 테스트", draft(activity).getText().toString()));
        }
    }
    private EditText draft(MainActivity activity) {
        try {
            Field field = MainActivity.class.getDeclaredField("titleInput");
            field.setAccessible(true);
            return (EditText)field.get(activity);
        } catch (Exception e) { throw new AssertionError(e); }
    }
}
