package com.example.system.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.system.config.RagProperties;
import com.example.system.model.ChatMessage;
import com.example.system.model.Citation;
import com.example.system.service.llm.OllamaClient;
import com.example.system.service.llm.OpenAiClient;

@Service
public class AnswerGeneratorService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\p{IsHan}+|[a-z0-9]+");
    private static final Set<String> STOP_TERMS = Set.of(
            "这个", "那个", "什么", "怎么", "如何", "为什么", "请问", "主要", "一下",
            "可以", "应该", "进行", "一个", "一些", "以及", "如果", "根据", "文档",
            "内容", "问题", "时候", "方面", "the", "and", "for", "with");

    private final RagProperties properties;
    private final OpenAiClient openAiClient;
    private final OllamaClient ollamaClient;

    public AnswerGeneratorService(RagProperties properties, OpenAiClient openAiClient, OllamaClient ollamaClient) {
        this.properties = properties;
        this.openAiClient = openAiClient;
        this.ollamaClient = ollamaClient;
    }

    public AnswerDraft generate(String question, List<SearchResult> results, List<ChatMessage> history) {
        List<Citation> citations = results.stream()
                .map(result -> new Citation(
                        result.chunk().getId(),
                        result.chunk().getChunkIndex(),
                        result.chunk().getContent(),
                        Math.round(result.score() * 10000.0) / 10000.0))
                .toList();

        if (realLlmEnabled()) {
            return new AnswerDraft(generateWithLlm(question, results, history), citations);
        }
        return new AnswerDraft(generateLocal(question, results, false), citations);
    }

    public String mode() {
        if (openAiClient.enabled()) {
            return "LLM 生成: " + properties.getOpenai().getChatModel() + "（OpenAI兼容）";
        }
        if (ollamaClient.enabled()) {
            return "LLM 生成: " + properties.getOllama().getChatModel() + "（Ollama本地）";
        }
        return "LLM 未配置：本地 RAG 兜底";
    }

    public boolean prefersDocumentOverview(String question) {
        String normalized = normalize(question);
        return normalized.contains("主要讲")
                || normalized.contains("讲了什么")
                || normalized.contains("主要内容")
                || normalized.contains("总结")
                || normalized.contains("概括")
                || normalized.contains("介绍一下")
                || normalized.contains("整体内容")
                || normalized.contains("这份文档");
    }

    private String generateWithLlm(String question, List<SearchResult> results, List<ChatMessage> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", "你是一个中文智能文档问答助手。只能依据给定文档片段回答，不能编造。"
                        + "如果文档片段不足以回答，请明确说明“文档中没有找到充分依据”。"
                        + "不要机械复制原文，要先理解用户问题，再综合片段给出清楚答案。"
                        + "回答应分点、简洁、贴题，并在关键结论后标注对应片段编号，例如[片段1]。"
                        + "如果用户问“主要讲什么”，请先给一句总括，再列出3到5个要点。"
                        + "如果用户问“怎么优化”，请给可执行建议，并说明建议依据。"));

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("检索到的文档片段：\n");
        for (int index = 0; index < results.size(); index++) {
            SearchResult result = results.get(index);
            userPrompt.append("[片段").append(index + 1)
                    .append(" | 原文块#").append(result.chunk().getChunkIndex())
                    .append(" | 相似度").append(String.format(Locale.ROOT, "%.4f", result.score()))
                    .append("]\n")
                    .append(truncate(result.chunk().getContent(), 1200))
                    .append("\n\n");
        }

        List<ChatMessage> recentHistory = history.stream()
                .skip(Math.max(0, history.size() - 6))
                .toList();
        if (!recentHistory.isEmpty()) {
            userPrompt.append("同一文档下的近期对话：\n");
            for (ChatMessage message : recentHistory) {
                userPrompt.append(message.getRole()).append(": ")
                        .append(truncate(message.getContent(), 400))
                        .append('\n');
            }
            userPrompt.append('\n');
        }

        userPrompt.append("用户问题：").append(question).append('\n');
        userPrompt.append("请基于上面的文档片段回答，并给出引用。不要使用未出现在片段中的事实。");
        messages.add(Map.of("role", "user", "content", userPrompt.toString()));
        if (openAiClient.enabled()) {
            return openAiClient.chat(messages);
        }
        return ollamaClient.chat(messages);
    }

    private boolean realLlmEnabled() {
        return openAiClient.enabled() || ollamaClient.enabled();
    }

    private String generateLocal(String question, List<SearchResult> results, boolean llmFailed) {
        if (results.isEmpty()) {
            return "当前文档还没有可检索的文本片段，请先上传可解析的 PDF 或 TXT 文档。";
        }

        String normalized = normalize(question);
        if (isImproveQuestion(normalized)) {
            return generateImprovementAnswer(results, llmFailed);
        }
        if (prefersDocumentOverview(question)) {
            return generateOverviewAnswer(results, llmFailed);
        }
        return generateDirectAnswer(question, results, llmFailed);
    }

    private String generateOverviewAnswer(List<SearchResult> results, boolean llmFailed) {
        String content = joinContents(results);
        List<String> keywords = topKeywords(content, 6);
        List<String> points = representativeSentences("", content, 4);

        StringBuilder answer = new StringBuilder();
        if (llmFailed) {
            answer.append("大模型接口暂时不可用，我先用本地 RAG 规则概括：\n\n");
        }
        answer.append("这份文档整体上主要围绕");
        if (keywords.isEmpty()) {
            answer.append("上传材料中的核心内容展开");
        } else {
            answer.append("“").append(String.join("、", keywords)).append("”展开");
        }
        answer.append("。\n\n");

        answer.append("可以概括成这几部分：\n");
        if (points.isEmpty()) {
            answer.append("1. 文档包含若干主题说明、问题记录和处理过程。\n");
            answer.append("2. 适合继续围绕关键章节做更细的提问。\n");
        } else {
            for (int index = 0; index < points.size(); index++) {
                answer.append(index + 1).append(". ").append(points.get(index)).append('\n');
            }
        }
        answer.append("\n右侧引用片段可以展开查看对应原文。");
        return answer.toString();
    }

    private String generateImprovementAnswer(List<SearchResult> results, boolean llmFailed) {
        String content = joinContents(results);
        Set<String> keywords = new HashSet<>(topKeywords(content, 12));

        StringBuilder answer = new StringBuilder();
        if (llmFailed) {
            answer.append("大模型接口暂时不可用，我先用本地 RAG 规则给出优化建议：\n\n");
        }
        answer.append("这个 PPT 可以从下面几个方向优化：\n\n");
        answer.append("1. 先加一页“核心结论”：用 3-4 句话说明做了什么、解决了什么问题、最后得到什么结果。\n");
        answer.append("2. 把“遇到的问题、原因分析、解决方法”整理成表格，现在这部分信息很多，表格会比长段文字更清楚。\n");
        answer.append("3. 每页只保留一个重点，长句拆成短 bullet；右侧引用里这种大段文字适合压缩成“问题 -> 原因 -> 方案”。\n");

        if (containsAny(keywords, "loss", "训练", "模型", "准确率", "参数", "调参")) {
            answer.append("4. 实验结果页建议用图表对比：Loss 曲线、准确率、学习率/batch size/epoch 的组合效果，结论放在图旁边。\n");
        } else {
            answer.append("4. 多放流程图或对比图，把文字型说明转成可视化结构。\n");
        }

        if (containsAny(keywords, "未来", "优化", "展望", "部署")) {
            answer.append("5. 结尾的未来规划可以分优先级：短期能做什么、中期优化什么、长期扩展什么。\n");
        } else {
            answer.append("5. 结尾补一页“后续计划”，让老师能看到项目还能怎么继续完善。\n");
        }

        answer.append("\n一句话说：少放整段文字，多放结构、图表和结论。");
        return answer.toString();
    }

    private String generateDirectAnswer(String question, List<SearchResult> results, boolean llmFailed) {
        String content = joinContents(results);
        List<String> sentences = representativeSentences(question, content, 3);

        StringBuilder answer = new StringBuilder();
        if (llmFailed) {
            answer.append("大模型接口暂时不可用，我先用本地检索结果回答：\n\n");
        }
        if (results.get(0).score() <= 0.02) {
            answer.append("这个问题和文档片段的匹配度不高，我按检索到的相关内容先回答：\n\n");
        } else {
            answer.append("根据文档中较相关的内容，可以回答：\n\n");
        }

        if (sentences.isEmpty()) {
            answer.append(truncate(results.get(0).chunk().getContent(), 280))
                    .append("（引用：原文块#")
                    .append(results.get(0).chunk().getChunkIndex())
                    .append("）");
            return answer.toString();
        }

        for (int index = 0; index < sentences.size(); index++) {
            answer.append(index + 1)
                    .append(". ")
                    .append(sentences.get(index))
                    .append('\n');
        }
        return answer.toString();
    }

    private List<String> representativeSentences(String question, String content, int limit) {
        Set<String> questionTerms = new HashSet<>(tokenize(question));
        List<String> rawSentences = splitSentences(content);
        LinkedHashSet<String> selected = new LinkedHashSet<>();

        rawSentences.stream()
                .map(sentence -> Map.entry(sentence, scoreSentence(sentence, questionTerms)))
                .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                .map(Map.Entry::getKey)
                .filter(sentence -> sentence.length() >= 8)
                .forEach(sentence -> {
                    if (selected.size() < limit) {
                        selected.add(truncate(sentence, 150));
                    }
                });

        if (selected.size() < limit) {
            for (String sentence : rawSentences) {
                if (selected.size() >= limit) {
                    break;
                }
                if (sentence.length() >= 8) {
                    selected.add(truncate(sentence, 150));
                }
            }
        }
        return new ArrayList<>(selected);
    }

    private int scoreSentence(String sentence, Set<String> questionTerms) {
        int score = 0;
        for (String term : tokenize(sentence)) {
            if (questionTerms.contains(term)) {
                score += 3;
            }
            if (term.equals("问题") || term.equals("原因") || term.equals("解决")
                    || term.equals("训练") || term.equals("模型") || term.equals("优化")) {
                score += 1;
            }
        }
        return score;
    }

    private List<String> splitSentences(String content) {
        return Pattern.compile("(?<=[。！？.!?])|\\n+")
                .splitAsStream(content)
                .map(String::trim)
                .filter(sentence -> !sentence.isBlank())
                .filter(sentence -> sentence.length() <= 220)
                .filter(sentence -> !sentence.matches("^[0-9０-９一二三四五六七八九十]+[、.．)]?$"))
                .toList();
    }

    private List<String> topKeywords(String content, int limit) {
        Map<String, Integer> counts = new HashMap<>();
        for (String token : tokenize(content)) {
            if (token.length() < 2 || STOP_TERMS.contains(token)) {
                continue;
            }
            counts.merge(token, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted((left, right) -> {
                    int countCompare = Integer.compare(right.getValue(), left.getValue());
                    if (countCompare != 0) {
                        return countCompare;
                    }
                    return left.getKey().compareTo(right.getKey());
                })
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<String> tokenize(String text) {
        String normalized = normalize(text);
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

    private String joinContents(List<SearchResult> results) {
        return results.stream()
                .sorted((left, right) -> Integer.compare(left.chunk().getChunkIndex(), right.chunk().getChunkIndex()))
                .map(result -> result.chunk().getContent())
                .collect(Collectors.joining("\n"));
    }

    private boolean isImproveQuestion(String normalizedQuestion) {
        return normalizedQuestion.contains("优化")
                || normalizedQuestion.contains("改进")
                || normalizedQuestion.contains("完善")
                || normalizedQuestion.contains("建议")
                || normalizedQuestion.contains("提升")
                || normalizedQuestion.contains("怎么改");
    }

    private boolean containsAny(Set<String> values, String... targets) {
        for (String target : targets) {
            if (values.contains(target)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)) + "...";
    }
}
