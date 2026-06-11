package com.example.system.service.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.system.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

@Component
public class OpenAiClient {

    private final RagProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public OpenAiClient(RagProperties properties) {
        this.properties = properties;
        this.mapper = JsonMapper.builder().findAndAddModules().build();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean enabled() {
        return properties.getOpenai().enabled();
    }

    public double[] embed(String text) {
        Map<String, Object> body = Map.of(
                "model", properties.getOpenai().getEmbeddingModel(),
                "input", text);
        JsonNode root = post("/embeddings", body);
        JsonNode embedding = root.path("data").path(0).path("embedding");
        double[] vector = new double[embedding.size()];
        for (int index = 0; index < embedding.size(); index++) {
            vector[index] = embedding.get(index).asDouble();
        }
        return normalize(vector);
    }

    public String chat(List<Map<String, String>> messages) {
        Map<String, Object> body = Map.of(
                "model", properties.getOpenai().getChatModel(),
                "messages", messages,
                "temperature", 0.2);
        JsonNode root = post("/chat/completions", body);
        return root.path("choices").path(0).path("message").path("content").asText();
    }

    private JsonNode post(String path, Object body) {
        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getOpenai().getBaseUrl() + path))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (properties.getOpenai().enabled()) {
                builder.header("Authorization", "Bearer " + properties.getOpenai().getApiKey());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI 兼容接口调用失败: HTTP "
                        + response.statusCode() + " " + response.body());
            }
            return mapper.readTree(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("OpenAI 兼容接口响应解析失败", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI 兼容接口调用被中断", ex);
        }
    }

    private double[] normalize(double[] vector) {
        double sum = 0;
        for (double value : vector) {
            sum += value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) {
            return vector;
        }
        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] / norm;
        }
        return vector;
    }
}
