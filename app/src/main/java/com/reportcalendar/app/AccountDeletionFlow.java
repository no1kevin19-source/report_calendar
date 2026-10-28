package com.reportcalendar.app;

/** Ordering and crash-recovery policy, testable without Android or production Firebase. */
final class AccountDeletionFlow {
    enum Stage { PROFILE_DELETE, AUTH_DELETE, RESTORE }
    enum Result { OK, ABSENT, REJECTED, UNKNOWN }
    interface Reply { void done(Result result); }
    interface Backend {
        void removeProfile(Reply reply);
        void removeIdentity(Reply reply);
        void verifyIdentity(Reply reply);
        void restoreProfile(Reply reply);
    }
    interface Journal { boolean save(Stage stage); boolean clear(); }
    interface Listener {
        void progress(String phase, String message);
        void finished(boolean deleted, String message);
    }
    private final Backend backend;
    private final Journal journal;
    private final Listener listener;
    AccountDeletionFlow(Backend backend, Journal journal, Listener listener) {
        this.backend = backend; this.journal = journal; this.listener = listener;
    }
    // Caller reauthenticates the exact user immediately before starting/retrying.
    void delete(boolean priorAmbiguousDelete) {
        // Never downgrade an earlier dispatched delete to a rollback-safe phase.
        if (!record(priorAmbiguousDelete ? Stage.AUTH_DELETE : Stage.PROFILE_DELETE)) return;
        listener.progress("profile", "내 정보를 삭제하고 있어요.");
        backend.removeProfile(profile -> {
            if (profile != Result.OK) {
                listener.finished(false, "내 정보가 삭제됐는지 확인하지 못해 이번 삭제를 멈췄어요. ‘계정 삭제 상태 확인’을 눌러 주세요.");
                return;
            }
            if (!record(Stage.AUTH_DELETE)) return;
            listener.progress("identity", "내 정보를 지웠어요. 이제 계정을 삭제하고 있어요.");
            backend.removeIdentity(identity -> {
                if (identity == Result.OK || identity == Result.ABSENT) complete();
                else if (identity == Result.REJECTED && !priorAmbiguousDelete) {
                    if (!record(Stage.RESTORE)) return;
                    listener.progress("restore", "계정을 삭제할 수 없어 내 정보를 복구하고 있어요.");
                    restore();
                } else listener.finished(false,
                    "계정이 삭제됐는지 확인하지 못했어요. 내 정보는 복구하지 않았어요. 인터넷 연결 후 ‘계정 삭제 상태 확인’을 눌러 주세요.");
            });
        });
    }
    void reconcile(Stage stage) {
        listener.progress("check", "계정이 삭제됐는지 확인하고 있어요.");
        if (stage == Stage.AUTH_DELETE) {
            backend.verifyIdentity(result -> {
                if (result == Result.ABSENT) complete();
                else listener.finished(false, result == Result.OK
                    ? "계정이 아직 남아 있어요. 삭제하려면 ‘계정 삭제’를 다시 눌러 주세요. 내 정보는 복구하지 않았어요."
                    : "계정 상태를 알 수 없어 내 정보를 복구하지 않았어요. 인터넷 연결과 로그인을 확인한 뒤 다시 시도해 주세요.");
            });
        } else restore();
    }
    private void restore() {
        backend.verifyIdentity(result -> {
            if (result == Result.ABSENT) {
                listener.finished(false, "계정은 없지만 내 정보까지 삭제됐는지는 확인이 필요해요. 앱 관리자에게 문의해 주세요.");
                return;
            }
            if (result != Result.OK) {
                listener.finished(false, "계정 상태를 알 수 없어 내 정보를 복구하지 않았어요. 인터넷 연결 후 다시 확인해 주세요.");
                return;
            }
            listener.progress("restore", "계정은 아직 남아 있어요. 내 정보를 복구하고 있어요.");
            backend.restoreProfile(restored -> {
                if (restored == Result.OK && journal.clear())
                    listener.finished(false, "계정은 삭제되지 않았어요. 내 정보는 복구했어요. 삭제하려면 본인 확인을 다시 해 주세요.");
                else listener.finished(false, "계정 삭제와 내 정보 복구가 끝났는지 확인하지 못했어요. ‘계정 삭제 상태 확인’을 눌러 주세요.");
            });
        });
    }
    private boolean record(Stage stage) {
        if (journal.save(stage)) return true;
        listener.finished(false, "휴대폰에 삭제 상태를 저장하지 못해 멈췄어요. 저장 공간을 확인해 주세요.");
        return false;
    }
    private void complete() {
        boolean cleared = journal.clear();
        listener.finished(true, cleared ? "계정과 내 정보를 삭제했어요. 휴대폰의 수행평가는 그대로예요."
            : "계정은 삭제했어요. 휴대폰의 삭제 기록은 정리하지 못했으니 저장 공간을 확인해 주세요.");
    }
}
