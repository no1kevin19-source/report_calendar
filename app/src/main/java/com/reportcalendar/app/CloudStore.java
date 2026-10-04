package com.reportcalendar.app;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import java.util.*;

/** User profiles only; no schedule data or credentials are uploaded. */
final class CloudStore {
    private static final Set<String> deletingUsers = new HashSet<>();

    static Task<Void> saveProfile(FirebaseApp app, FirebaseUser user) {
        if (deletingUsers.contains(user.getUid())) {
            return Tasks.forException(new IllegalStateException("Account deletion in progress"));
        }
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", user.getUid());
        profile.put("email", user.getEmail() == null ? "" : user.getEmail());
        profile.put("displayName", user.getDisplayName() == null ? "" : user.getDisplayName());
        profile.put("updatedAt", FieldValue.serverTimestamp());
        // The Authentication UID is the document ID: retries update one document, never add one.
        return FirebaseFirestore.getInstance(app).collection("users").document(user.getUid())
            .set(profile, SetOptions.merge());
    }

    static Task<Void> deleteAccount(FirebaseUser user) {
        String uid = user.getUid();
        if (!deletingUsers.add(uid)) {
            return Tasks.forException(new IllegalStateException("Account deletion in progress"));
        }
        FirebaseApp app = FirebaseApp.getInstance();
        FirebaseFirestore db = FirebaseFirestore.getInstance(app);
        DocumentReference profile = db.collection("users").document(uid);
        // A transaction requires a server connection. Do not queue an offline deletion and
        // then sign out before Firestore has acknowledged it.
        Task<Void> cleanup = db.<Void>runTransaction(transaction -> {
            transaction.get(profile);
            transaction.delete(profile);
            return null;
        });
        return cleanup.continueWithTask(removed -> {
            if (!removed.isSuccessful()) return Tasks.<Void>forException(removed.getException());
            return user.delete().continueWithTask(deleted -> {
                if (deleted.isSuccessful()) return Tasks.<Void>forResult(null);
                Exception failure = deleted.getException();
                // If Firebase rejected account deletion, restore its profile only after
                // confirming that the account still exists. Never revive a deleted account.
                return user.reload().continueWithTask(reloaded -> {
                    if (!reloaded.isSuccessful()) return Tasks.<Void>forException(failure);
                    deletingUsers.remove(uid);
                    return saveProfile(app, user).continueWithTask(restored -> Tasks.<Void>forException(failure));
                });
            });
        }).addOnCompleteListener(result -> deletingUsers.remove(uid));
    }
}
