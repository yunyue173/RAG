package com.example.system.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.system.config.RagProperties;
import com.example.system.service.embedding.EmbeddingService;
import com.example.system.service.llm.OpenAiClient;

class EmbeddingServiceTests {

    @Test
    void localEmbeddingMakesRelatedTextCloser() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingDimension(128);
        EmbeddingService service = new EmbeddingService(properties, new OpenAiClient(properties));

        double[] question = service.embed("系统如何上传 PDF 文档");
        double[] related = service.embed("用户可以上传 PDF 或 TXT 文档，系统会解析文本。");
        double[] unrelated = service.embed("番茄炒蛋需要鸡蛋和番茄，先炒蛋再炒菜。");

        assertTrue(cosine(question, related) > cosine(question, unrelated));
    }

    private double cosine(double[] left, double[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
