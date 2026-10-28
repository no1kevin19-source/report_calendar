package com.reportcalendar.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class AssignmentPublicationTest {
    @Test public void publicPayloadContainsOnlyWhitelistedFields() {
        Map<String, Object> data = AssignmentPublication.fields("id", "owner", "과제", "과학", "1교시", "내용", 1L, false);
        assertEquals(new HashSet<>(Arrays.asList("id", "ownerUid", "visibility", "title", "subject", "period", "detail", "dueMillis", "complete")), data.keySet());
        assertEquals("public", data.get("visibility"));
        assertFalse(data.containsKey("photos"));
        assertFalse(data.containsKey("email"));
    }
    @Test public void ownerPolicyRejectsOtherAccountAndSignedOut() {
        assertTrue(AssignmentPublication.canWrite("", "alice"));
        assertTrue(AssignmentPublication.canWrite("alice", "alice"));
        assertFalse(AssignmentPublication.canWrite("alice", "bob"));
        assertFalse(AssignmentPublication.canWrite("", null));
    }
    @Test(expected = IllegalArgumentException.class) public void emptyTitleRejected() {
        AssignmentPublication.fields("id", "owner", "", "과학", "1교시", "", 1L, false);
    }
    @Test(expected = IllegalArgumentException.class) public void oversizedDetailRejected() {
        AssignmentPublication.fields("id", "owner", "제목", "과학", "1교시", String.join("", Collections.nCopies(10001, "x")), 1L, false);
    }
    @Test(expected = IllegalArgumentException.class) public void negativeDateRejected() {
        AssignmentPublication.fields("id", "owner", "제목", "과학", "1교시", "", -1L, false);
    }
    @Test public void validBoundaryAndCompletedTaskAccepted() {
        Map<String, Object> data = AssignmentPublication.fields("id", "owner", String.join("", Collections.nCopies(200, "x")), "과학", "1교시", "", 253402300799999L, true);
        assertEquals(true, data.get("complete"));
    }
}
