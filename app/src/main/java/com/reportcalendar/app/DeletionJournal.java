package com.reportcalendar.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Separate from calendar preferences. No password, OAuth token or profile snapshot. */
final class DeletionJournal implements AccountDeletionFlow.Journal {
    private final SharedPreferences prefs;
    private final String uid;
    DeletionJournal(Context context, String uid) {
        prefs = context.getSharedPreferences("auth_deletion_recovery", Context.MODE_PRIVATE);
        this.uid = uid;
    }
    static String pendingUid(Context context) {
        return context.getSharedPreferences("auth_deletion_recovery", Context.MODE_PRIVATE).getString("uid", "");
    }
    AccountDeletionFlow.Stage stage() {
        try { return AccountDeletionFlow.Stage.valueOf(prefs.getString("stage", "AUTH_DELETE")); }
        catch (IllegalArgumentException error) { return AccountDeletionFlow.Stage.AUTH_DELETE; }
    }
    public boolean save(AccountDeletionFlow.Stage stage) {
        // Persist BEFORE dispatching the mutation; apply() isn't crash-safe enough here.
        return prefs.edit().putString("uid", uid).putString("stage", stage.name()).commit();
    }
    public boolean clear() {
        return !uid.equals(prefs.getString("uid", "")) || prefs.edit().clear().commit();
    }
}
