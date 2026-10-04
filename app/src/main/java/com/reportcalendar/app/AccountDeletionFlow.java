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
        listener.progress("profile", "사용자 정보 삭제 결과를 확인하고 있어요.");
        backend.removeProfile(profile -> {
            if (profile != Result.OK) {
                listener.finished(false, "사용자 정보 삭제를 확인하지 못했어요. 계정 삭제는 요청하지 않았어요. ‘삭제 처리 결과 확인’으로 확인해주세요.");
                return;
            }
            if (!record(Stage.AUTH_DELETE)) return;
            listener.progress("identity", "사용자 정보가 삭제되었어요. 로그인 계정을 삭제하고 있어요.");
            backend.removeIdentity(identity -> {
                if (identity == Result.OK || identity == Result.ABSENT) complete();
                else if (identity == Result.REJECTED && !priorAmbiguousDelete) {
                    if (!record(Stage.RESTORE)) return;
                    listener.progress("restore", "계정 삭제가 거절되었어요. 다시 인증해야 할 수 있어요. 사용자 정보 복구를 확인하고 있어요.");
                    restore();
                } else listener.finished(false,
                    "계정 삭제 결과가 불확실해요. 사용자 정보를 임의로 복구하지 않았어요. 연결 후 ‘삭제 처리 결과 확인’을 눌러주세요.");
            });
        });
    }
    void reconcile(Stage stage) {
        listener.progress("check", "이전 삭제 처리의 실제 계정 상태를 확인하고 있어요.");
        if (stage == Stage.AUTH_DELETE) {
            backend.verifyIdentity(result -> {
                if (result == Result.ABSENT) complete();
                else listener.finished(false, result == Result.OK
                    ? "계정은 아직 존재해요. 이전 삭제 요청의 결과가 불확실하므로 프로필을 다시 만들지 않았어요. ‘계정 삭제’를 눌러 재인증 후 삭제를 재시도할 수 있어요."
                    : "계정 상태를 확인하지 못했어요. 프로필을 복구하지 않았어요. 연결과 로그인을 확인한 뒤 다시 확인해주세요.");
            });
        } else restore();
    }
    private void restore() {
        backend.verifyIdentity(result -> {
            if (result == Result.ABSENT) {
                listener.finished(false, "로그인 계정이 없는 것으로 확인되어 프로필 복구를 중단했어요. 프로필 삭제 완료 여부는 관리자 확인이 필요해요.");
                return;
            }
            if (result != Result.OK) {
                listener.finished(false, "계정 존재 여부를 확인하지 못해 사용자 정보 복구를 보류했어요. 연결 후 다시 확인해주세요.");
                return;
            }
            listener.progress("restore", "계정이 존재함을 확인했어요. 사용자 정보를 복구하고 있어요. 아직 삭제는 완료되지 않았어요.");
            backend.restoreProfile(restored -> {
                if (restored == Result.OK && journal.clear())
                    listener.finished(false, "계정은 삭제되지 않았고 사용자 정보는 복구되었어요. 삭제하려면 다시 인증해주세요.");
                else listener.finished(false, "계정 삭제가 완료되지 않았으며 사용자 정보 복구도 확인하지 못했어요. ‘삭제 처리 결과 확인’으로 재시도해주세요.");
            });
        });
    }
    private boolean record(Stage stage) {
        if (journal.save(stage)) return true;
        listener.finished(false, "삭제 진행 기록을 저장하지 못했어요. 추가 삭제 요청은 중단했어요. 저장 공간을 확인해주세요.");
        return false;
    }
    private void complete() {
        boolean cleared = journal.clear();
        listener.finished(true, cleared ? "계정과 사용자 정보가 삭제되었어요. 기기의 수행평가는 유지됩니다."
            : "계정은 삭제되었지만 기기의 처리 기록을 정리하지 못했어요. 저장 공간을 확인해주세요.");
    }
}
