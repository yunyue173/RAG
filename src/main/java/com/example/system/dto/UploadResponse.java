package com.example.system.dto;

import com.example.system.model.Conversation;
import com.example.system.model.DocumentRecord;

public record UploadResponse(DocumentRecord document, Conversation conversation) {
}
