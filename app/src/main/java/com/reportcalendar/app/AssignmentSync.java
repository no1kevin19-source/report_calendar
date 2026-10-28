package com.reportcalendar.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import org.json.*;
import java.util.*;

/** Application-owned durable outbox. No Activity or credentials are retained. */
final class AssignmentSync {
    private static AssignmentSync instance;
    static synchronized AssignmentSync get(Context context) {
        if (instance == null) instance = new AssignmentSync(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<Runnable> observers = new HashSet<>();
    private final Set<String> running = new HashSet<>();
    private String message = "";
    private AssignmentSync(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences("report_calendar_native", Context.MODE_PRIVATE);
    }
    private JSONObject pending() {
        try { return new JSONObject(prefs.getString("assignment_public_outbox", "{}")); }
        catch (JSONException e) { throw new IllegalStateException("공개 과제 저장 상태를 읽을 수 없습니다", e); }
    }
    // Local schedule and its publication intention are committed together, before network I/O.
    synchronized boolean save(JSONArray local, String documentId, String ownerUid, Map<String, Object> data) {
        try {
            JSONObject queue = pending();
            if (!documentId.isEmpty()) {
                JSONObject item = new JSONObject();
                item.put("ownerUid", ownerUid);
                item.put("revision", UUID.randomUUID().toString());
                item.put("delete", data == null);
                if (data != null) item.put("data", new JSONObject(data));
                queue.put(documentId, item);
            }
            boolean saved = prefs.edit().putString("assignments", local.toString())
                .putString("assignment_public_outbox", queue.toString()).commit();
            if (saved) message = "";
            notifyUi();
            return saved;
        } catch (JSONException | IllegalStateException e) { return false; }
    }
    synchronized boolean hasPending(String id) { return pending().has(id); }
    synchronized String status() {
        int count = pending().length();
        if (count == 0) return "저장을 기다리는 공개 과제가 없어요.";
        return "공개 과제 저장·삭제 " + count + "개가 아직 끝나지 않았어요. " + (message.isEmpty()
            ? "완료 전에는 다른 사람에게 이전 내용이 보일 수 있어요." : message);
    }
    synchronized void observe(Runnable callback) { observers.add(callback); callback.run(); }
    synchronized void unobserve(Runnable callback) { observers.remove(callback); }
    private void notifyUi() {
        handler.post(() -> { for (Runnable observer : new ArrayList<>(observers)) observer.run(); });
    }
    synchronized void retry() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) { message = "등록한 계정으로 로그인한 뒤 다시 시도해 주세요."; notifyUi(); return; }
        if (user.getUid().equals(DeletionJournal.pendingUid(context))) {
            message = "계정 삭제가 진행 중이라 공개 과제를 저장할 수 없어요."; notifyUi(); return;
        }
        JSONObject queue = pending();
        Iterator<String> keys = queue.keys();
        while (keys.hasNext()) {
            String id = keys.next();
            JSONObject item = queue.optJSONObject(id);
            if (item == null || !user.getUid().equals(item.optString("ownerUid")) || running.contains(id)) continue;
            send(id, item);
        }
        notifyUi();
    }
    private void send(String id, JSONObject item) {
        String uid = item.optString("ownerUid");
        String revision = item.optString("revision");
        boolean delete = item.optBoolean("delete");
        Map<String, Object> fields = new HashMap<>();
        if (!delete) {
            JSONObject data = item.optJSONObject("data");
            if (data == null) return;
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) { String key = keys.next(); fields.put(key, data.opt(key)); }
            fields.put("updatedAt", FieldValue.serverTimestamp());
        }
        running.add(id);
        DocumentReference ref = FirebaseFirestore.getInstance().collection("assignments").document(id);
        // Transactions fail offline instead of silently enqueuing stale SDK writes across accounts.
        FirebaseFirestore.getInstance().runTransaction(tx -> {
            FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
            if (current == null || current.isAnonymous() || !uid.equals(current.getUid())
                    || uid.equals(DeletionJournal.pendingUid(context))) throw new IllegalStateException("Account changed");
            DocumentSnapshot previous = tx.get(ref);
            if (previous.exists() && !uid.equals(previous.getString("ownerUid")))
                throw new IllegalStateException("Owner mismatch");
            if (delete) { if (previous.exists()) tx.delete(ref); }
            else tx.set(ref, fields);
            return null;
        }).addOnCompleteListener(task -> {
            synchronized (AssignmentSync.this) {
                running.remove(id);
                JSONObject queue = pending();
                JSONObject latest = queue.optJSONObject(id);
                boolean same = latest != null && revision.equals(latest.optString("revision"));
                if (task.isSuccessful() && same) {
                    queue.remove(id);
                    if (!prefs.edit().putString("assignment_public_outbox", queue.toString()).commit())
                        message = "저장 상태를 확인하지 못했어요. 다시 시도해 주세요.";
                    else message = "";
                } else if (!task.isSuccessful()) {
                    message = "공개 과제가 바뀌었는지 확인하지 못했어요. 인터넷 연결과 로그인 계정을 확인한 뒤 다시 시도해 주세요.";
                }
                notifyUi();
                // A newer edit/delete waits for the old attempt to finish, never runs in parallel.
                if (latest != null && !same) retry();
            }
        });
        handler.postDelayed(() -> {
            synchronized (AssignmentSync.this) {
                if (running.contains(id)) {
                    message = "조금 오래 걸리고 있어요. 아직 저장·삭제가 끝났는지는 알 수 없어요.";
                    notifyUi();
                }
            }
        }, 20000);
    }
}
