package com.hubu.energy;

public final class RefreshSessionTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        RefreshSession session = new RefreshSession();
        session.begin(100, false);
        check(session.startRead() == null, "a refresh must not read cached DOM before navigation");
        session.committed();
        check(session.startRead() == null, "an old commit before navigation cannot unlock reading");
        session.navigated(); session.committed();
        RefreshSession.Read oldPage = session.startRead();
        check(oldPage != null, "fresh document can be inspected");
        check(session.startRead() == null, "only one asynchronous read can run at a time");
        session.navigated(); session.committed();
        RefreshSession.Read newPage = session.startRead();
        check(!session.completeRead(oldPage), "a redirect invalidates the previous document callback");
        check(session.completeRead(newPage), "new page callback remains valid after an old callback arrives");
        check(!session.completeRead(newPage), "a callback can only be accepted once");
        RefreshSession.Read priorRun = session.startRead();
        session.stop(); session.begin(200, false); session.navigated(); session.committed();
        RefreshSession.Read currentRun = session.startRead();
        check(!session.completeRead(priorRun), "late success from an earlier refresh must not replace current data");
        check(session.completeRead(currentRun), "current refresh result can complete");
        check(!session.expired(30199), "refresh should retain the full timeout window");
        check(session.expired(30200), "background refresh times out instead of spinning forever");
        session.stop();
        check(!session.expired(999999), "finished refresh does not emit another timeout");
        session.begin(500, true);
        check(!session.expired(30500), "interactive login gets enough time for user input");
        check(session.expired(300500), "interactive login is also bounded");
        check(session.interactive(), "interactive intent is retained");
        session.navigated(); session.committed();
        RefreshSession.Read cancelled = session.startRead(); session.stop();
        check(!session.completeRead(cancelled), "cancelled refresh cannot save a late result");
        System.out.println("RefreshSession: asynchronous refresh, redirects, cancellation and timeout checks passed");
    }
}
