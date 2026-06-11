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
public class OllamaClient {

    private final RagProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public OllamaClient(RagProperties properties) {
        this.properties = properties;
        this.mapper = JsonMapper.builder().findAndAddModules().build();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public boolean enabled() {
        return properties.getOllama().isEnabled();
    }

    public String chat(List<Map<String, String>> messages) {
        Map<String, Object> body = Map.of(
                "model", properties.getOllama().getChatModel(),
                "messages", messages,
                "stream", false,
                "options", Map.of("temperature", 0.2));
        JsonNode root = post("/api/chat", body);
        return root.path("message").path("content").asText();
    }

    private JsonNode post(String path, Object body) {
        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getOllama().getBaseUrl() + path))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Ollama 调用失败: HTTP " + response.statusCode() + " " + response.body());
            }
            return mapper.readTree(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("Ollama 未连接或响应解析失败", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama 调用被中断", ex);
        }
    }
}
