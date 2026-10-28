package com.reportcalendar.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.*;
import java.util.*;

final class ReminderScheduler {
    static final String CHANNEL = "assignment_deadlines";
    static final String ENABLED = "reminder_enabled";
    static final String PENDING = "reminder_pending_at";
    static final String DELIVERED = "reminder_delivered_at";
    static final String ACTION = "com.reportcalendar.app.REMIND";
    private static final int ALARM_ID = 8201, NOTICE_ID = 8202;
    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("report_calendar_native", Context.MODE_PRIVATE);
    }
    static void channel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "수행평가 마감 알림", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("설정한 날짜와 시간에 미완료 수행평가를 알려드려요.");
            c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }
    static boolean allowed(Context c) {
        channel(c);
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 24 && !nm.areNotificationsEnabled()) return false;
        return Build.VERSION.SDK_INT < 26 || nm.getNotificationChannel(CHANNEL).getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    static boolean exact(Context c) {
        return Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }
    private static PendingIntent alarm(Context c, long at) {
        return PendingIntent.getBroadcast(c, ALARM_ID, new Intent(c, ReminderReceiver.class)
            .setAction(ACTION).putExtra("at", at), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private static JSONArray assignments(SharedPreferences p) {
        try { return new JSONArray(p.getString("assignments", "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }
    private static long at(JSONObject item, SharedPreferences p) {
        if (item == null || item.optBoolean("complete", false)) return -1;
        return ReminderTime.trigger(item.optLong("dueMillis", 0), p.getInt("reminder_days", 0),
            p.getString("reminder_time", null), TimeZone.getDefault());
    }
    static synchronized void reschedule(Context c) {
        channel(c);
        SharedPreferences p = prefs(c);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        long old = p.getLong(PENDING, -1), now = System.currentTimeMillis(), next = Long.MAX_VALUE;
        am.cancel(alarm(c, old));
        if (p.getBoolean(ENABLED, false) && allowed(c)) {
            JSONArray items = assignments(p);
            for (int i = 0; i < items.length(); i++) {
                long at = at(items.optJSONObject(i), p);
                if (ReminderTime.eligible(at, now, old, p.getLong(DELIVERED, -1), TimeZone.getDefault()))
                    next = Math.min(next, at);
            }
        }
        if (next == Long.MAX_VALUE) {
            p.edit().remove(PENDING).commit();
            return;
        }
        // Persist before registering so reboot recovery can catch an overdue alarm today.
        if (!p.edit().putLong(PENDING, next).commit()) return;
        PendingIntent intent = alarm(c, next);
        long fireAt = Math.max(next, now + 1000);
        try {
            if (exact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, intent);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, intent);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, intent);
        }
    }
    static synchronized void receive(Context c, long scheduled) {
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        if (!p.getBoolean(ENABLED, false) || scheduled != p.getLong(PENDING, -1)
                || scheduled <= p.getLong(DELIVERED, -1) || scheduled > now
                || !ReminderTime.sameDay(scheduled, now, TimeZone.getDefault()) || !allowed(c)) {
            reschedule(c); return;
        }
        JSONArray items = assignments(p);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (at(item, p) == scheduled) lines.add(item.optString("subject") + " · " + item.optString("title"));
        }
        if (!lines.isEmpty()) {
            // Persist the receipt first; duplicate broadcasts must not alert twice.
            if (!p.edit().putLong(DELIVERED, scheduled).remove(PENDING).commit()) return;
            post(c, NOTICE_ID, "수행평가 " + lines.size() + "개 · "
                + ReminderTime.DAYS[p.getInt("reminder_days", 0)] + "일 전", lines);
        }
        reschedule(c);
    }
    static boolean test(Context c) {
        return post(c, 8203, "테스트 알림", Collections.singletonList("수행평가 알림이 이렇게 표시돼요."));
    }
    private static boolean post(Context c, int id, String title, List<String> lines) {
        if (!allowed(c)) return false;
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent click = PendingIntent.getActivity(c, 8204, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        Notification.InboxStyle style = new Notification.InboxStyle();
        for (int i = 0; i < Math.min(lines.size(), 6); i++) style.addLine(lines.get(i));
        if (lines.size() > 6) style.setSummaryText("외 " + (lines.size() - 6) + "개");
        b.setSmallIcon(R.drawable.ic_notifications).setContentTitle(title).setContentText(lines.get(0))
            .setStyle(style).setContentIntent(click).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE);
        if (Build.VERSION.SDK_INT < 26) b.setDefaults(Notification.DEFAULT_ALL);
        try { c.getSystemService(NotificationManager.class).notify(id, b.build()); return true; }
        catch (SecurityException e) { return false; }
    }
}
