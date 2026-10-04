package com.reportcalendar.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static com.reportcalendar.app.AccountDeletionFlow.Result.*;
import static com.reportcalendar.app.AccountDeletionFlow.Stage.*;

public class AccountDeletionFlowTest {
    static class Fixture implements AccountDeletionFlow.Backend, AccountDeletionFlow.Journal, AccountDeletionFlow.Listener {
        final List<String> calls = new ArrayList<>();
        AccountDeletionFlow.Reply pending;
        AccountDeletionFlow.Stage stored;
        boolean writable = true, deleted, finished;
        String message = "";
        final AccountDeletionFlow flow = new AccountDeletionFlow(this, this, this);
        public void removeProfile(AccountDeletionFlow.Reply r) { calls.add("profile"); pending = r; }
        public void removeIdentity(AccountDeletionFlow.Reply r) { calls.add("identity"); pending = r; }
        public void verifyIdentity(AccountDeletionFlow.Reply r) { calls.add("verify"); pending = r; }
        public void restoreProfile(AccountDeletionFlow.Reply r) { calls.add("restore"); pending = r; }
        public boolean save(AccountDeletionFlow.Stage stage) { if (!writable) return false; stored = stage; return true; }
        public boolean clear() { if (!writable) return false; stored = null; return true; }
        public void progress(String phase, String text) { message = text; }
        public void finished(boolean success, String text) { finished = true; deleted = success; message = text; }
        void reply(AccountDeletionFlow.Result result) { AccountDeletionFlow.Reply r = pending; pending = null; r.done(result); }
    }
    @Test public void successfulDeletionRecordsBeforeEveryMutation() {
        Fixture f = new Fixture(); f.flow.delete(false);
        assertEquals(PROFILE_DELETE, f.stored);
        assertEquals(Arrays.asList("profile"), f.calls);
        f.reply(OK);
        assertEquals(AUTH_DELETE, f.stored);
        assertEquals(Arrays.asList("profile", "identity"), f.calls);
        f.reply(OK);
        assertTrue(f.deleted); assertNull(f.stored);
    }
    @Test public void noIdentityDeleteIfProfileDeleteNotConfirmed() {
        Fixture f = new Fixture(); f.flow.delete(false); f.reply(UNKNOWN);
        assertEquals(Arrays.asList("profile"), f.calls);
        assertFalse(f.deleted); assertEquals(PROFILE_DELETE, f.stored);
    }
    @Test public void rejectedDeletionConfirmsExistenceBeforeRestore() {
        Fixture f = new Fixture(); f.flow.delete(false); f.reply(OK); f.reply(REJECTED);
        assertEquals(RESTORE, f.stored);
        assertEquals("verify", f.calls.get(2));
        assertTrue(f.message.contains("거절"));
        f.reply(OK); assertEquals("restore", f.calls.get(3)); f.reply(OK);
        assertFalse(f.deleted); assertNull(f.stored);
    }
    @Test public void networkErrorOnIdentityNeverRestoresProfile() {
        Fixture f = new Fixture(); f.flow.delete(false); f.reply(OK); f.reply(UNKNOWN);
        assertEquals(Arrays.asList("profile", "identity"), f.calls);
        assertEquals(AUTH_DELETE, f.stored); assertTrue(f.finished);
    }
    @Test public void noRestoreWhenAccountIsGone() {
        Fixture f = new Fixture(); f.flow.delete(false); f.reply(OK); f.reply(REJECTED); f.reply(ABSENT);
        assertFalse(f.calls.contains("restore")); assertFalse(f.deleted);
        assertTrue(f.message.contains("관리자"));
    }
    @Test public void noRestoreWhenExistenceUnknown() {
        Fixture f = new Fixture(); f.flow.delete(false); f.reply(OK); f.reply(REJECTED); f.reply(UNKNOWN);
        assertFalse(f.calls.contains("restore")); assertEquals(RESTORE, f.stored);
    }
    @Test public void recoveryFailureIsVisibleAndJournalRetained() {
        Fixture f = new Fixture(); f.flow.delete(false); f.reply(OK); f.reply(REJECTED); f.reply(OK); f.reply(UNKNOWN);
        assertEquals(RESTORE, f.stored);
        assertTrue(f.message.contains("복구도 확인하지 못"));
    }
    @Test public void crashAfterAuthDispatchAndConfirmedAbsenceFinishes() {
        Fixture f = new Fixture(); f.stored = AUTH_DELETE; f.flow.reconcile(f.stored); f.reply(ABSENT);
        assertTrue(f.deleted); assertNull(f.stored); assertEquals(Arrays.asList("verify"), f.calls);
    }
    @Test public void crashWithAmbiguousAuthDeleteNeverRestoresEvenIfUserExists() {
        Fixture f = new Fixture(); f.stored = AUTH_DELETE; f.flow.reconcile(f.stored); f.reply(OK);
        assertFalse(f.deleted); assertEquals(AUTH_DELETE, f.stored);
        assertEquals(Arrays.asList("verify"), f.calls);
    }
    @Test public void crashBeforeAuthDispatchCanRepairAfterExistenceCheck() {
        Fixture f = new Fixture(); f.stored = PROFILE_DELETE; f.flow.reconcile(f.stored); f.reply(OK); f.reply(OK);
        assertEquals(Arrays.asList("verify", "restore"), f.calls); assertNull(f.stored);
    }
    @Test public void crashDuringRestoreRetriesSafely() {
        Fixture f = new Fixture(); f.stored = RESTORE; f.flow.reconcile(f.stored); f.reply(OK); f.reply(OK);
        assertFalse(f.deleted); assertNull(f.stored);
    }
    @Test public void journalFailurePreventsFirstMutation() {
        Fixture f = new Fixture(); f.writable = false; f.flow.delete(false);
        assertTrue(f.calls.isEmpty()); assertTrue(f.finished);
    }
    @Test public void journalFailurePreventsIdentityMutation() {
        Fixture f = new Fixture(); f.flow.delete(false); f.writable = false; f.reply(OK);
        assertEquals(Arrays.asList("profile"), f.calls); assertEquals(PROFILE_DELETE, f.stored);
    }
    @Test public void pendingCallbackDoesNotPretendDeletionHasFailed() {
        Fixture f = new Fixture(); f.flow.delete(false); f.reply(OK);
        assertFalse(f.finished); assertEquals(AUTH_DELETE, f.stored);
        f.reply(OK); assertTrue(f.deleted);
    }
    @Test public void retryNeverDowngradesAmbiguousDeleteOnProfileFailure() {
        Fixture f = new Fixture(); f.stored = AUTH_DELETE; f.flow.delete(true); f.reply(UNKNOWN);
        assertEquals(AUTH_DELETE, f.stored);
        f.flow.reconcile(f.stored); f.reply(OK);
        assertFalse(f.calls.contains("restore"));
    }
    @Test public void retryRejectionDoesNotRollbackEarlierAmbiguousDelete() {
        Fixture f = new Fixture(); f.stored = AUTH_DELETE; f.flow.delete(true); f.reply(OK); f.reply(REJECTED);
        assertEquals(AUTH_DELETE, f.stored); assertFalse(f.calls.contains("restore"));
    }
}
