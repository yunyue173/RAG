package com.example.system.service;

import com.example.system.model.DocumentChunk;

public record SearchResult(DocumentChunk chunk, double score) {
}
