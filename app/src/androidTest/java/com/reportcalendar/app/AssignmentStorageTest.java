package com.reportcalendar.app;

import android.content.*;
import android.os.Build;
import android.provider.Settings;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

/** Offline disposable-emulator tests; no production Firebase writes or account creation. */
@RunWith(AndroidJUnit4.class)
public class AssignmentStorageTest {
    private Context context;
    private SharedPreferences prefs;
    private Map<String, ?> before;
    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Disposable emulator required", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"));
        assertEquals(1, Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0));
        assertEquals(0, Settings.Global.getInt(context.getContentResolver(), Settings.Global.WIFI_ON, 0));
        prefs = context.getSharedPreferences("report_calendar_native", 0);
        before = new HashMap<>(prefs.getAll());
        prefs.edit().clear().putString("assignments", "[]").commit();
        FirebaseApp.initializeApp(context);
        FirebaseAuth.getInstance().signOut();
    }
    @After public void restore() {
        if (before == null) return;
        SharedPreferences.Editor e = prefs.edit().clear();
        for (Map.Entry<String, ?> item : before.entrySet()) {
            Object v = item.getValue(); String k = item.getKey();
            if (v instanceof String) e.putString(k, (String)v);
            else if (v instanceof Integer) e.putInt(k, (Integer)v);
            else if (v instanceof Long) e.putLong(k, (Long)v);
            else if (v instanceof Boolean) e.putBoolean(k, (Boolean)v);
            else if (v instanceof Float) e.putFloat(k, (Float)v);
            else if (v instanceof Set) e.putStringSet(k, (Set<String>)v);
        }
        assertTrue(e.commit());
        assertEquals(before, prefs.getAll());
    }
    private Object field(Object target, String name) {
        try { Field f=target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private void call(Object target, String name) {
        try { Method m=target.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(target); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private JSONArray saved() {
        try { return new JSONArray(prefs.getString("assignments", "[]")); }
        catch (JSONException e) { throw new AssertionError(e); }
    }
    private JSONObject queue() {
        try { return new JSONObject(prefs.getString("assignment_public_outbox", "{}")); }
        catch (JSONException e) { throw new AssertionError(e); }
    }
    private void fill(MainActivity a) {
        ((EditText)field(a,"titleInput")).setText("테스트 과제");
        ((Spinner)field(a,"subjectSpinner")).setSelection(5);
        ((Spinner)field(a,"periodSpinner")).setSelection(1);
    }
    @Test public void legacyJsonDefaultsToPrivateAndNeverQueues() throws Exception {
        prefs.edit().putString("assignments", "[{\"id\":\"legacy\",\"title\":\"기존 과제\",\"photos\":[\"content://local/photo\"]}]").commit();
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            screen.onActivity(a -> {
                Object item=((List<?>)field(a,"assignments")).get(0);
                assertEquals(false, field(item,"isPublic"));
                assertEquals("", field(item,"cloudId"));
                assertEquals(1, ((List<?>)field(item,"photos")).size());
                assertEquals(0, queue().length());
            });
        }
        assertEquals("legacy", saved().getJSONObject(0).getString("id"));
    }
    @Test public void privateRegistrationIsLocalOnly() throws Exception {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            screen.onActivity(a -> { fill(a); call(a,"saveAssignmentFromForm"); });
        }
        assertEquals(1, saved().length());
        assertEquals("private", saved().getJSONObject(0).getString("visibility"));
        assertEquals(0, queue().length());
    }
    @Test public void publicRegistrationRequiresLoginWithoutLosingDraft() {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            screen.onActivity(a -> {
                fill(a); ((Spinner)field(a,"visibilitySpinner")).setSelection(1);
                call(a,"saveAssignmentFromForm");
                assertEquals(0, saved().length());
                assertEquals("테스트 과제", ((EditText)field(a,"titleInput")).getText().toString());
            });
        }
    }
    @Test public void rotationKeepsVisibilityAndDraft() {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            screen.onActivity(a -> { fill(a); ((Spinner)field(a,"visibilitySpinner")).setSelection(1); });
            screen.recreate();
            screen.onActivity(a -> {
                assertEquals(1, ((Spinner)field(a,"visibilitySpinner")).getSelectedItemPosition());
                assertEquals("테스트 과제", ((EditText)field(a,"titleInput")).getText().toString());
            });
        }
    }
    @Test public void publicOutboxIsIdempotentAndPrivateTransitionContainsNoText() throws Exception {
        AssignmentSync sync = AssignmentSync.get(context);
        Map<String,Object> fields=AssignmentPublication.fields("doc", "alice", "공개", "과학", "1교시", "내용", 1L, false);
        assertTrue(sync.save(new JSONArray(), "doc", "alice", fields));
        String first=queue().getJSONObject("doc").getString("revision");
        assertTrue(sync.save(new JSONArray(), "doc", "alice", fields));
        assertEquals(1, queue().length());
        assertNotEquals(first, queue().getJSONObject("doc").getString("revision"));
        assertTrue(sync.save(new JSONArray(), "doc", "alice", null));
        JSONObject removal=queue().getJSONObject("doc");
        assertTrue(removal.getBoolean("delete"));
        assertFalse(removal.has("data"));
        assertFalse(removal.toString().contains("내용"));
        assertEquals("alice", removal.getString("ownerUid"));
    }
    @Test public void privateSavePreservesOtherPendingPublicWrites() throws Exception {
        AssignmentSync sync=AssignmentSync.get(context);
        assertTrue(sync.save(new JSONArray(), "doc", "alice", null));
        assertTrue(sync.save(new JSONArray("[{\"id\":\"private\"}]"), "", "", null));
        assertEquals(1, queue().length());
        assertTrue(queue().getJSONObject("doc").getBoolean("delete"));
        assertEquals("private", saved().getJSONObject(0).getString("id"));
    }
    @Test public void unpublishQueuesDeletionNotPrivateContent() throws Exception {
        prefs.edit().putString("assignments", "[{\"id\":\"mine\",\"cloudId\":\"doc\",\"ownerUid\":\"alice\",\"visibility\":\"public\",\"title\":\"공개\"}]").commit();
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            screen.onActivity(a -> {
                try {
                    Object item=((List<?>)field(a,"assignments")).get(0);
                    Field visibility=item.getClass().getDeclaredField("isPublic"); visibility.setAccessible(true); visibility.set(item,false);
                    Field title=item.getClass().getDeclaredField("title"); title.setAccessible(true); title.set(item,"비밀 내용");
                    Method save=a.getClass().getDeclaredMethod("savePublication",item.getClass(),boolean.class); save.setAccessible(true);
                    assertEquals(true, save.invoke(a,item,false));
                    assertTrue(queue().getJSONObject("doc").getBoolean("delete"));
                    assertFalse(queue().toString().contains("비밀"));
                    assertEquals("private",saved().getJSONObject(0).getString("visibility"));
                    assertEquals("doc",saved().getJSONObject(0).getString("cloudId"));
                } catch (Exception e) { throw new AssertionError(e); }
            });
        }
    }
    @Test public void confirmedUnpublishBecomesEditableLocalTask() throws Exception {
        prefs.edit().putString("assignments", "[{\"id\":\"mine\",\"cloudId\":\"doc\",\"ownerUid\":\"alice\",\"visibility\":\"private\",\"title\":\"비공개\",\"subject\":\"과학\",\"period\":\"1교시\"}]").commit();
        try (ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)) {
            screen.onActivity(a -> {
                try {
                    Object item=((List<?>)field(a,"assignments")).get(0);
                    Method edit=a.getClass().getDeclaredMethod("loadAssignmentIntoForm",item.getClass()); edit.setAccessible(true); edit.invoke(a,item);
                    ((EditText)field(a,"titleInput")).setText("로컬 수정");
                    call(a,"saveAssignmentFromForm");
                    assertEquals("",saved().getJSONObject(0).getString("cloudId"));
                    assertEquals("로컬 수정",saved().getJSONObject(0).getString("title"));
                    assertEquals(0,queue().length());
                } catch (Exception e) {throw new AssertionError(e);}
            });
        }
    }
}
