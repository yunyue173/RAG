package com.example.system.dto;

public record AppStatusResponse(String embeddingMode, String answerMode, int chunkSize, int chunkOverlap, int topK) {
}
