package com.reportcalendar.app;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;

/** User profiles only. Transactions avoid offline writes reviving a deleted profile. */
final class CloudStore {
    private static final Map<String, List<Task<Void>>> writes = new HashMap<>();
    static synchronized Task<Void> saveProfile(FirebaseApp app, FirebaseUser user) {
        String uid = user.getUid();
        if (uid.equals(DeletionJournal.pendingUid(app.getApplicationContext())))
            return Tasks.forException(new IllegalStateException("Deletion requires reconciliation"));
        Task<Void> task = writeProfile(app, user, false);
        if (!writes.containsKey(uid)) writes.put(uid, new ArrayList<>());
        writes.get(uid).add(task);
        task.addOnCompleteListener(done -> {
            synchronized (CloudStore.class) {
                List<Task<Void>> pending = writes.get(uid);
                if (pending != null) { pending.remove(task); if (pending.isEmpty()) writes.remove(uid); }
            }
        });
        return task;
    }
    private static Task<Void> writeProfile(FirebaseApp app, FirebaseUser user, boolean recovery) {
        FirebaseFirestore db = FirebaseFirestore.getInstance(app);
        DocumentReference ref = db.collection("users").document(user.getUid());
        Map<String, Object> fields = new HashMap<>();
        fields.put("uid", user.getUid());
        fields.put("email", user.getEmail() == null ? "" : user.getEmail());
        fields.put("displayName", user.getDisplayName() == null ? "" : user.getDisplayName());
        fields.put("updatedAt", FieldValue.serverTimestamp());
        return db.runTransaction(tx -> {
            if (!recovery && user.getUid().equals(DeletionJournal.pendingUid(app.getApplicationContext())))
                throw new IllegalStateException("Deletion requires reconciliation");
            tx.get(ref);
            tx.set(ref, fields, SetOptions.merge());
            return null;
        });
    }
    static AccountDeletionFlow.Backend deletionBackend(FirebaseUser user) {
        FirebaseApp app = FirebaseApp.getInstance();
        FirebaseFirestore db = FirebaseFirestore.getInstance(app);
        DocumentReference ref = db.collection("users").document(user.getUid());
        return new AccountDeletionFlow.Backend() {
            public void removeProfile(AccountDeletionFlow.Reply reply) {
                List<Task<Void>> pending;
                synchronized (CloudStore.class) {
                    List<Task<Void>> existing = writes.get(user.getUid());
                    pending = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
                }
                // Drain already-dispatched profile writes before deleting the document.
                Tasks.whenAllComplete(pending).continueWithTask(ignored -> db.<Void>runTransaction(tx -> {
                    tx.get(ref); tx.delete(ref); return null;
                })).addOnCompleteListener(task -> reply.done(task.isSuccessful()
                    ? AccountDeletionFlow.Result.OK : AccountDeletionFlow.Result.UNKNOWN));
            }
            public void removeIdentity(AccountDeletionFlow.Reply reply) {
                user.delete().addOnCompleteListener(task -> reply.done(task.isSuccessful()
                    ? AccountDeletionFlow.Result.OK : classify(task.getException(), true)));
            }
            public void verifyIdentity(AccountDeletionFlow.Reply reply) {
                user.reload().addOnCompleteListener(task -> reply.done(task.isSuccessful()
                    ? AccountDeletionFlow.Result.OK : classify(task.getException(), false)));
            }
            public void restoreProfile(AccountDeletionFlow.Reply reply) {
                writeProfile(app, user, true).addOnCompleteListener(task -> reply.done(task.isSuccessful()
                    ? AccountDeletionFlow.Result.OK : AccountDeletionFlow.Result.UNKNOWN));
            }
        };
    }
    private static AccountDeletionFlow.Result classify(Exception error, boolean deleting) {
        if (error instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) error).getErrorCode();
            if ("ERROR_USER_NOT_FOUND".equals(code)) return AccountDeletionFlow.Result.ABSENT;
            if (deleting && "ERROR_REQUIRES_RECENT_LOGIN".equals(code)) return AccountDeletionFlow.Result.REJECTED;
        }
        // Invalid tokens, disabled users and network failures are NOT proof of deletion.
        return AccountDeletionFlow.Result.UNKNOWN;
    }
}
