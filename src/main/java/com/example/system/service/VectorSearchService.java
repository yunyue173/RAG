package com.example.system.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.system.model.DocumentChunk;
import com.example.system.repository.RagRepository;
import com.example.system.service.embedding.EmbeddingService;

@Service
public class VectorSearchService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\p{IsHan}+|[a-z0-9]+");
    private static final Set<String> STOP_TERMS = Set.of(
            "这个", "那个", "什么", "怎么", "如何", "为什么", "请问", "主要", "一下",
            "可以", "应该", "进行", "一个", "一些", "以及", "如果", "根据", "文档",
            "内容", "问题", "时候", "方面", "the", "and", "for", "with");

    private final RagRepository repository;
    private final EmbeddingService embeddingService;
    private final AnswerGeneratorService answerGeneratorService;

    public VectorSearchService(RagRepository repository, EmbeddingService embeddingService,
            AnswerGeneratorService answerGeneratorService) {
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.answerGeneratorService = answerGeneratorService;
    }

    public List<SearchResult> search(String documentId, String question, int topK) {
        List<DocumentChunk> chunks = repository.findChunksByDocument(documentId);
        if (chunks.isEmpty()) {
            return List.of();
        }

        double[] queryVector = embeddingService.embed(question);
        Set<String> queryTerms = new HashSet<>(tokenize(question));
        boolean overviewQuestion = answerGeneratorService.prefersDocumentOverview(question);

        return chunks.stream()
                .map(chunk -> new SearchResult(
                        chunk,
                        hybridScore(chunk, chunks.size(), queryVector, queryTerms, overviewQuestion)))
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed()
                        .thenComparing(result -> result.chunk().getChunkIndex()))
                .limit(Math.max(1, topK))
                .toList();
    }

    private double hybridScore(DocumentChunk chunk, int totalChunks, double[] queryVector,
            Set<String> queryTerms, boolean overviewQuestion) {
        String content = chunk.getContent() == null ? "" : chunk.getContent();
        double vectorScore = Math.max(0, cosine(queryVector, vectorForChunk(chunk, queryVector.length)));
        double keywordScore = keywordScore(content, queryTerms);
        double structureScore = structureScore(content, chunk.getChunkIndex(), totalChunks);
        double codePenalty = codePenalty(content);

        double score;
        if (overviewQuestion) {
            score = vectorScore * 0.20 + keywordScore * 0.20 + structureScore * 0.55 - codePenalty * 0.35;
        } else {
            score = vectorScore * 0.55 + keywordScore * 0.35 + structureScore * 0.10 - codePenalty * 0.10;
        }
        return Math.max(0, Math.round(score * 10000.0) / 10000.0);
    }

    private double[] vectorForChunk(DocumentChunk chunk, int queryVectorLength) {
        double[] stored = chunk.getVector();
        if (embeddingService.remoteEmbeddingEnabled()
                && stored != null
                && stored.length == queryVectorLength) {
            return stored;
        }
        return embeddingService.embed(chunk.getContent());
    }

    private double keywordScore(String content, Set<String> queryTerms) {
        if (queryTerms.isEmpty()) {
            return 0;
        }
        Set<String> contentTerms = new HashSet<>(tokenize(content));
        int hits = 0;
        for (String term : queryTerms) {
            if (contentTerms.contains(term)) {
                hits++;
            }
        }
        return Math.min(1.0, hits / (double) Math.max(1, queryTerms.size()));
    }

    private double structureScore(String content, int chunkIndex, int totalChunks) {
        String text = content.toLowerCase(Locale.ROOT);
        double score = 0;
        if (text.contains("实验目的") || text.contains("项目背景") || text.contains("课程设计要求")) {
            score += 0.45;
        }
        if (text.contains("实验内容") || text.contains("功能需求") || text.contains("主要内容")) {
            score += 0.25;
        }
        if (text.contains("总结") || text.contains("结论") || text.contains("未来") || text.contains("展望")) {
            score += 0.20;
        }
        if (chunkIndex <= 2) {
            score += 0.20;
        }
        if (totalChunks > 2 && chunkIndex >= totalChunks - 1) {
            score += 0.10;
        }
        return Math.min(1.0, score);
    }

    private double codePenalty(String content) {
        String text = content.toLowerCase(Locale.ROOT);
        int codeMarkers = 0;
        String[] markers = {
                "public ", "private ", "class ", "import ", "driverManager".toLowerCase(Locale.ROOT),
                "preparedstatement", "system.out", "request.", "response.", "conn.", "stmt.", "{", "};"
        };
        for (String marker : markers) {
            if (text.contains(marker)) {
                codeMarkers++;
            }
        }
        long semicolons = text.chars().filter(ch -> ch == ';').count();
        if (semicolons >= 3) {
            codeMarkers++;
        }
        return Math.min(1.0, codeMarkers / 5.0);
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

    private double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0) {
            return 0;
        }
        int length = Math.min(left.length, right.length);
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
