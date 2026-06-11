package com.example.system.dto;

import java.time.Instant;

public record HistorySearchResult(
        String documentId,
        String documentName,
        String conversationId,
        String conversationTitle,
        String messageId,
        String messageRole,
        String snippet,
        Instant matchedAt) {
}
