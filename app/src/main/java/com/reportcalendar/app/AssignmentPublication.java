package com.reportcalendar.app;

import java.util.HashMap;
import java.util.Map;

/** Explicit allowlist: local photo URIs and private data never enter a public payload. */
final class AssignmentPublication {
    static Map<String, Object> fields(String id, String ownerUid, String title, String subject,
            String period, String detail, long dueMillis, boolean complete) {
        require(id, 1, 200); require(ownerUid, 1, 128);
        require(title, 1, 200); require(subject, 1, 100);
        require(period, 1, 20); require(detail, 0, 10000);
        if (dueMillis < 0 || dueMillis > 253402300799999L)
            throw new IllegalArgumentException("마감일을 확인해 주세요");
        Map<String, Object> data = new HashMap<>();
        data.put("id", id); data.put("ownerUid", ownerUid); data.put("visibility", "public");
        data.put("title", title); data.put("subject", subject); data.put("period", period);
        data.put("detail", detail); data.put("dueMillis", dueMillis); data.put("complete", complete);
        return data;
    }
    private static void require(String value, int min, int max) {
        if (value == null || value.length() < min || value.length() > max)
            throw new IllegalArgumentException("공개 과제는 과제명 200자, 과목 100자, 내용 10,000자 이내로 입력해 주세요");
    }
    static boolean canWrite(String owner, String currentUid) {
        return currentUid != null && (owner.isEmpty() || owner.equals(currentUid));
    }
}
