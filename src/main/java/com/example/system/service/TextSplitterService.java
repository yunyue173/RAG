package com.example.system.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.system.config.RagProperties;

@Service
public class TextSplitterService {

    private final RagProperties properties;

    public TextSplitterService(RagProperties properties) {
        this.properties = properties;
    }

    public List<TextSlice> split(String text) {
        int chunkSize = Math.max(100, properties.getChunkSize());
        int overlap = Math.max(0, Math.min(properties.getChunkOverlap(), chunkSize / 2));
        List<TextSlice> slices = new ArrayList<>();

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkSize);
            int paragraphEnd = findParagraphBoundary(text, start, end, chunkSize);
            if (paragraphEnd > start) {
                end = paragraphEnd;
            }

            String content = text.substring(start, end).trim();
            if (!content.isBlank()) {
                slices.add(new TextSlice(start, end, content));
            }

            if (end >= text.length()) {
                break;
            }
            start = Math.max(0, end - overlap);
        }

        return slices;
    }

    private int findParagraphBoundary(String text, int start, int end, int chunkSize) {
        int min = start + Math.min(120, chunkSize / 3);
        for (int index = end - 1; index >= min; index--) {
            char ch = text.charAt(index);
            if (ch == '\n' || ch == '。' || ch == '！' || ch == '？' || ch == '.' || ch == '!' || ch == '?') {
                return index + 1;
            }
        }
        return end;
    }
}
