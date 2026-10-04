package com.reportcalendar.app;

/** Process-owned request gate. Display data only; no credentials or Views. */
final class AuthOperationState {
    long id, revision;
    boolean busy, accountCreated;
    String kind = "", phase = "", message = "", email = "";
    long begin(String kind, String email) {
        if (busy) return 0;
        id++; revision++; busy = true; accountCreated = false;
        this.kind = kind; this.email = email == null ? "" : email;
        phase = "request"; message = "처리 중이에요…";
        return id;
    }
    boolean accepts(long token) { return busy && id == token; }
    void progress(long token, String phase, String message) {
        if (!accepts(token)) return;
        this.phase = phase; this.message = message; revision++;
    }
    void finish(long token, String message) {
        if (!accepts(token)) return;
        busy = false; phase = "finished"; this.message = message; revision++;
    }
}
