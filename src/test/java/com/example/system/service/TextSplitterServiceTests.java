package com.example.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.system.config.RagProperties;

class TextSplitterServiceTests {

    @Test
    void splitKeepsLongTextInMultipleChunks() {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(120);
        properties.setChunkOverlap(20);
        TextSplitterService service = new TextSplitterService(properties);

        String text = "第一段说明系统支持 PDF 上传和 TXT 上传。".repeat(12);
        List<TextSlice> slices = service.split(text);

        assertTrue(slices.size() > 1);
        assertTrue(slices.stream().allMatch(slice -> !slice.content().isBlank()));
        assertEquals(0, slices.getFirst().startOffset());
    }
}
