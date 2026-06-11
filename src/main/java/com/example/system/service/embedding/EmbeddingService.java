package com.example.system.service.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.system.config.RagProperties;
import com.example.system.service.llm.OpenAiClient;

@Service
public class EmbeddingService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\p{IsHan}+|[a-z0-9]+");
    private static final Set<String> STOP_TERMS = Set.of(
            "这个", "那个", "什么", "怎么", "如何", "为什么", "请问", "主要", "一下",
            "可以", "应该", "进行", "一个", "一些", "以及", "如果", "根据", "文档",
            "内容", "问题", "时候", "方面", "the", "and", "for", "with");

    private final RagProperties properties;
    private final OpenAiClient openAiClient;

    public EmbeddingService(RagProperties properties, OpenAiClient openAiClient) {
        this.properties = properties;
        this.openAiClient = openAiClient;
    }

    public double[] embed(String text) {
        if (remoteEmbeddingEnabled()) {
            try {
                return openAiClient.embed(text);
            } catch (RuntimeException ex) {
                // API 不可用时自动走本地向量，保证课程演示不中断。
                return hashEmbedding(text);
            }
        }
        return hashEmbedding(text);
    }

    public String mode() {
        return remoteEmbeddingEnabled()
                ? "OpenAI " + properties.getOpenai().getEmbeddingModel() + "（失败自动本地兜底）"
                : "本地中文关键词向量";
    }

    public boolean remoteEmbeddingEnabled() {
        return openAiClient.enabled() && properties.getOpenai().isEmbeddingEnabled();
    }

    private double[] hashEmbedding(String text) {
        int dimension = Math.max(64, properties.getEmbeddingDimension());
        double[] vector = new double[dimension];
        List<String> tokens = tokenize(text);

        for (String token : tokens) {
            addTerm(vector, token, 1.0);
        }
        for (int index = 0; index < tokens.size() - 1; index++) {
            addTerm(vector, tokens.get(index) + "_" + tokens.get(index + 1), 1.3);
        }
        return normalize(vector);
    }

    private List<String> tokenize(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (token.matches("\\p{IsHan}+")) {
                addChineseTerms(tokens, token);
            } else if (token.length() >= 2 && !STOP_TERMS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private void addChineseTerms(List<String> tokens, String text) {
        String cleaned = text.replaceAll("[的了是一在有和与或及也就都而被把很更最这那你我他她它吗呢吧啊]", "");
        for (int size = 2; size <= 3; size++) {
            for (int index = 0; index + size <= cleaned.length(); index++) {
                String term = cleaned.substring(index, index + size);
                if (!STOP_TERMS.contains(term)) {
                    tokens.add(term);
                }
            }
        }
        if (cleaned.length() == 1 && !STOP_TERMS.contains(cleaned)) {
            tokens.add(cleaned);
        }
    }

    private void addTerm(double[] vector, String term, double weight) {
        int index = Math.floorMod(term.hashCode(), vector.length);
        vector[index] += weight;
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
