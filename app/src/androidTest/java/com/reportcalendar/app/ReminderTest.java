package com.reportcalendar.app;

import android.app.*;
import android.content.*;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import static org.junit.Assert.*;

/** Run only on the disposable QA emulator; no Firebase calls or production accounts. */
@RunWith(AndroidJUnit4.class)
public class ReminderTest {
    Context c; SharedPreferences p; long nowMinute;
    @Before public void setup() {
        c = ApplicationProvider.getApplicationContext();
        assertTrue("Disposable emulator required", android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"));
        assertEquals(1, android.provider.Settings.Global.getInt(c.getContentResolver(), "airplane_mode_on", 0));
        p = ReminderScheduler.prefs(c);
        p.edit().clear().commit(); c.getSystemService(NotificationManager.class).cancelAll();
        nowMinute = System.currentTimeMillis() / 60000 * 60000;
        ReminderScheduler.channel(c);
    }
    @After public void cleanup() {
        p.edit().clear().commit(); ReminderScheduler.reschedule(c);
        c.getSystemService(NotificationManager.class).cancelAll();
    }
    private void seed(boolean complete) throws Exception {
        Calendar due = Calendar.getInstance(); due.setTimeInMillis(nowMinute); due.add(Calendar.DATE, 1);
        JSONObject item = new JSONObject().put("id", "qa-reminder").put("title", "알림 테스트 과제")
            .put("subject", "과학").put("dueMillis", due.getTimeInMillis()).put("complete", complete);
        p.edit().putBoolean(ReminderScheduler.ENABLED, true).putInt("reminder_days", 0)
            .putString("reminder_time", new SimpleDateFormat("HH:mm", Locale.US).format(new Date(nowMinute)))
            .putString("assignments", new JSONArray().put(item).toString())
            .putLong(ReminderScheduler.PENDING, nowMinute).commit();
    }
    @Test public void sendsAndDoesNotRepeat() throws Exception {
        seed(false); ReminderScheduler.receive(c, nowMinute);
        assertEquals(1, c.getSystemService(NotificationManager.class).getActiveNotifications().length);
        assertEquals(nowMinute, p.getLong(ReminderScheduler.DELIVERED, -1));
        c.getSystemService(NotificationManager.class).cancelAll();
        ReminderScheduler.receive(c, nowMinute);
        assertEquals(0, c.getSystemService(NotificationManager.class).getActiveNotifications().length);
    }
    @Test public void completedAndDeletedAreNotSent() throws Exception {
        seed(true); ReminderScheduler.receive(c, nowMinute);
        assertEquals(0, c.getSystemService(NotificationManager.class).getActiveNotifications().length);
        seed(false); p.edit().putString("assignments", "[]").commit();
        ReminderScheduler.receive(c, nowMinute);
        assertEquals(0, c.getSystemService(NotificationManager.class).getActiveNotifications().length);
        assertFalse(p.contains(ReminderScheduler.PENDING));
    }
    @Test public void oldSettingsDoNotOptIn() throws Exception {
        seed(false); p.edit().remove(ReminderScheduler.ENABLED).commit();
        ReminderScheduler.reschedule(c); ReminderScheduler.receive(c, nowMinute);
        assertEquals(0, c.getSystemService(NotificationManager.class).getActiveNotifications().length);
        assertFalse(p.contains(ReminderScheduler.PENDING));
    }
    @Test public void disabledCancelsPending() throws Exception {
        seed(false); p.edit().putBoolean(ReminderScheduler.ENABLED, false).commit();
        ReminderScheduler.reschedule(c);
        assertFalse(p.contains(ReminderScheduler.PENDING));
    }
    @Test public void staleAlarmAfterSettingsChangeDoesNotSend() throws Exception {
        seed(false); p.edit().putInt("reminder_days", 3).commit();
        ReminderScheduler.receive(c, nowMinute);
        assertEquals(0, c.getSystemService(NotificationManager.class).getActiveNotifications().length);
    }
    @Test public void systemAlarmActuallyReachesReceiver() throws Exception {
        seed(false);
        ReminderScheduler.reschedule(c);
        long end = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < end
                && c.getSystemService(NotificationManager.class).getActiveNotifications().length == 0)
            Thread.sleep(200);
        assertEquals(nowMinute, p.getLong(ReminderScheduler.DELIVERED, -1));
        assertEquals(1, c.getSystemService(NotificationManager.class).getActiveNotifications().length);
    }
    @Test public void rebootReceiverRestoresFutureReservation() throws Exception {
        seed(false);
        JSONArray items = new JSONArray(p.getString("assignments", "[]"));
        Calendar due = Calendar.getInstance(); due.setTimeInMillis(nowMinute); due.add(Calendar.DATE, 2);
        items.getJSONObject(0).put("dueMillis", due.getTimeInMillis());
        p.edit().putString("assignments", items.toString()).remove(ReminderScheduler.PENDING).commit();
        new ReminderReceiver().onReceive(c, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertTrue(p.getLong(ReminderScheduler.PENDING, -1) > System.currentTimeMillis());
        long expected = p.getLong(ReminderScheduler.PENDING, -1);
        ReminderScheduler.reschedule(c);
        assertEquals(expected, p.getLong(ReminderScheduler.PENDING, -1));
    }
}
