package com.reportcalendar.app;

import android.content.*;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (ReminderScheduler.ACTION.equals(intent.getAction()))
            ReminderScheduler.receive(context, intent.getLongExtra("at", -1));
        else ReminderScheduler.reschedule(context);
    }
}
