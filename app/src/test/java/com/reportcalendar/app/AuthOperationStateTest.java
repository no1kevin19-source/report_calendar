package com.reportcalendar.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class AuthOperationStateTest {
    @Test public void duplicateLoginAndRegistrationCannotOverlap() {
        AuthOperationState state = new AuthOperationState();
        long first = state.begin("register", "test@example.invalid");
        assertTrue(first > 0);
        assertEquals(0, state.begin("register", "other@example.invalid"));
        assertEquals(0, state.begin("google", ""));
        assertEquals("test@example.invalid", state.email);
    }
    @Test public void recreatedScreenObservesSameRequestAndCompletion() {
        AuthOperationState process = new AuthOperationState();
        long id = process.begin("login", "test@example.invalid");
        AuthOperationState resumedScreen = process;
        assertTrue(resumedScreen.busy);
        process.finish(id, "signed in");
        assertFalse(resumedScreen.busy);
        assertEquals("signed in", resumedScreen.message);
    }
    @Test public void timeoutNoticeDoesNotUnlockMutationOrIgnoreLateSuccess() {
        AuthOperationState state = new AuthOperationState();
        long id = state.begin("delete", "");
        state.progress(id, "identity", "result unknown after timeout");
        assertEquals(0, state.begin("delete", ""));
        state.finish(id, "deleted");
        assertEquals("deleted", state.message);
    }
    @Test public void staleCallbacksCannotEndNextRequest() {
        AuthOperationState state = new AuthOperationState();
        long old = state.begin("google", "");
        state.finish(old, "cancelled");
        long next = state.begin("login", "");
        state.finish(old, "late Google success");
        state.progress(old, "profile", "stale progress");
        assertTrue(state.accepts(next));
        assertEquals("request", state.phase);
    }
    @Test public void createdAccountSurvivesFollowupFailureInDisplayState() {
        AuthOperationState state = new AuthOperationState();
        long id = state.begin("register", "test@example.invalid");
        state.accountCreated = true;
        state.finish(id, "profile or mail not confirmed");
        assertTrue(state.accountCreated);
        assertEquals("register", state.kind);
        assertEquals("test@example.invalid", state.email);
    }
    @Test public void completionIsIdempotent() {
        AuthOperationState state = new AuthOperationState();
        long id = state.begin("reset", "");
        state.finish(id, "accepted");
        long revision = state.revision;
        state.finish(id, "duplicated result");
        assertEquals(revision, state.revision);
        assertEquals("accepted", state.message);
    }
}
