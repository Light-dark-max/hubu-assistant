package com.hubu.energy;

/** Tracks one user-requested refresh; callbacks from older pages cannot complete it. */
final class RefreshSession {
    static final long REFRESH_TIMEOUT_MS = 30000;
    static final long LOGIN_TIMEOUT_MS = 300000;
    static final class Read {
        final long run, document;
        Read(long run, long document) { this.run = run; this.document = document; }
    }
    private long run, document, deadline;
    private boolean active, interactive, navigated, committed, pendingRead;

    void begin(long now, boolean interactive) {
        run++; document++; active = true; this.interactive = interactive;
        deadline = now + (interactive ? LOGIN_TIMEOUT_MS : REFRESH_TIMEOUT_MS);
        navigated = false; committed = false; pendingRead = false;
    }
    void navigated() {
        if (!active) return;
        document++; navigated = true; committed = false; pendingRead = false;
    }
    void committed() { if (active && navigated) committed = true; }
    Read startRead() {
        if (!active || !committed || pendingRead) return null;
        pendingRead = true;
        return new Read(run, document);
    }
    boolean completeRead(Read read) {
        if (!active || read == null || read.run != run || read.document != document || !pendingRead) return false;
        pendingRead = false;
        return true;
    }
    boolean expired(long now) { return active && now >= deadline; }
    boolean active() { return active; }
    boolean interactive() { return interactive; }
    void stop() { run++; active = false; pendingRead = false; committed = false; }
}
