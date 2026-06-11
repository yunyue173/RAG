package com.example.system.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.system.config.RagProperties;
import com.example.system.dto.HistorySearchResult;
import com.example.system.model.ChatMessage;
import com.example.system.model.Conversation;
import com.example.system.model.DocumentChunk;
import com.example.system.model.DocumentRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import jakarta.annotation.PostConstruct;

@Repository
public class RagRepository {

    private final RagProperties properties;
    private final ObjectMapper mapper;
    private final Map<String, DocumentRecord> documents = new LinkedHashMap<>();
    private final List<DocumentChunk> chunks = new ArrayList<>();
    private final Map<String, Conversation> conversations = new LinkedHashMap<>();
    private final List<ChatMessage> messages = new ArrayList<>();

    public RagRepository(RagProperties properties) {
        this.properties = properties;
        this.mapper = JsonMapper.builder().findAndAddModules().build();
    }

    @PostConstruct
    public void load() {
        try {
            Files.createDirectories(properties.storagePath());
            loadDocuments();
            loadChunks();
            loadConversations();
            loadMessages();
        } catch (IOException ex) {
            throw new IllegalStateException("无法初始化本地 RAG 存储目录", ex);
        }
    }

    public synchronized List<DocumentRecord> findAllDocuments() {
        return documents.values().stream()
                .sorted(Comparator.comparing(DocumentRecord::getUploadedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
    }

    public synchronized List<DocumentRecord> findDocumentsByOwner(String ownerUserId) {
        return documents.values().stream()
                .filter(document -> ownerUserId.equals(document.getOwnerUserId()))
                .sorted(Comparator.comparing(DocumentRecord::getUploadedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
    }

    public synchronized Optional<DocumentRecord> findDocument(String documentId) {
        return Optional.ofNullable(documents.get(documentId));
    }

    public synchronized Optional<DocumentRecord> findDocumentForOwner(String documentId, String ownerUserId) {
        return findDocument(documentId)
                .filter(document -> ownerUserId.equals(document.getOwnerUserId()));
    }

    public synchronized List<DocumentChunk> findChunksByDocument(String documentId) {
        return chunks.stream()
                .filter(chunk -> chunk.getDocumentId().equals(documentId))
                .sorted(Comparator.comparingInt(DocumentChunk::getChunkIndex))
                .toList();
    }

    public synchronized void saveDocumentWithChunks(DocumentRecord document, List<DocumentChunk> documentChunks) {
        documents.put(document.getId(), document);
        chunks.removeIf(chunk -> chunk.getDocumentId().equals(document.getId()));
        chunks.addAll(documentChunks);
        persistAll();
    }

    public synchronized void deleteDocument(String documentId) {
        documents.remove(documentId);
        chunks.removeIf(chunk -> chunk.getDocumentId().equals(documentId));
        List<String> conversationIds = conversations.values().stream()
                .filter(conversation -> conversation.getDocumentId().equals(documentId))
                .map(Conversation::getId)
                .toList();
        conversationIds.forEach(conversations::remove);
        messages.removeIf(message -> conversationIds.contains(message.getConversationId()));
        persistAll();
    }

    public synchronized Conversation saveConversation(Conversation conversation) {
        conversations.put(conversation.getId(), conversation);
        persistConversations();
        return conversation;
    }

    public synchronized Optional<Conversation> findConversation(String conversationId) {
        return Optional.ofNullable(conversations.get(conversationId));
    }

    public synchronized List<Conversation> findConversationsByDocument(String documentId) {
        return conversations.values().stream()
                .filter(conversation -> conversation.getDocumentId().equals(documentId))
                .sorted(Comparator.comparing(Conversation::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
    }

    public synchronized void appendMessages(String conversationId, ChatMessage userMessage, ChatMessage assistantMessage) {
        messages.add(userMessage);
        messages.add(assistantMessage);
        Conversation conversation = conversations.get(conversationId);
        if (conversation != null) {
            conversation.setUpdatedAt(Instant.now());
        }
        persistMessages();
        persistConversations();
    }

    public synchronized List<ChatMessage> findMessagesByConversation(String conversationId) {
        return messages.stream()
                .filter(message -> message.getConversationId().equals(conversationId))
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public synchronized List<HistorySearchResult> searchHistory(String keyword, int limit) {
        return searchHistory(keyword, limit, null);
    }

    public synchronized List<HistorySearchResult> searchHistory(String keyword, int limit, String ownerUserId) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }

        List<HistorySearchResult> results = new ArrayList<>();
        for (Conversation conversation : conversations.values()) {
            DocumentRecord document = documents.get(conversation.getDocumentId());
            if (document == null || !belongsToOwner(document, ownerUserId)) {
                continue;
            }
            boolean conversationMatched = contains(document.getFilename(), normalizedKeyword)
                    || contains(conversation.getTitle(), normalizedKeyword);
            if (conversationMatched) {
                results.add(new HistorySearchResult(
                        document.getId(),
                        document.getFilename(),
                        conversation.getId(),
                        conversation.getTitle(),
                        null,
                        "conversation",
                        "匹配文档或会话标题：" + conversation.getTitle(),
                        conversation.getUpdatedAt()));
            }
        }

        for (ChatMessage message : messages) {
            if (!contains(message.getContent(), normalizedKeyword)) {
                continue;
            }
            Conversation conversation = conversations.get(message.getConversationId());
            if (conversation == null) {
                continue;
            }
            DocumentRecord document = documents.get(conversation.getDocumentId());
            if (document == null || !belongsToOwner(document, ownerUserId)) {
                continue;
            }
            results.add(new HistorySearchResult(
                    document.getId(),
                    document.getFilename(),
                    conversation.getId(),
                    conversation.getTitle(),
                    message.getId(),
                    message.getRole(),
                    snippet(message.getContent(), keyword),
                    message.getCreatedAt()));
        }

        return results.stream()
                .sorted(Comparator.comparing(HistorySearchResult::matchedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(Math.max(1, Math.min(50, limit)))
                .toList();
    }

    private boolean belongsToOwner(DocumentRecord document, String ownerUserId) {
        return ownerUserId == null || ownerUserId.equals(document.getOwnerUserId());
    }

    private void loadDocuments() throws IOException {
        Path path = file("documents.json");
        if (Files.exists(path)) {
            List<DocumentRecord> loaded = mapper.readValue(path.toFile(), new TypeReference<>() {
            });
            loaded.forEach(document -> documents.put(document.getId(), document));
        }
    }

    private void loadChunks() throws IOException {
        Path path = file("chunks.json");
        if (Files.exists(path)) {
            chunks.addAll(mapper.readValue(path.toFile(), new TypeReference<List<DocumentChunk>>() {
            }));
        }
    }

    private void loadConversations() throws IOException {
        Path path = file("conversations.json");
        if (Files.exists(path)) {
            List<Conversation> loaded = mapper.readValue(path.toFile(), new TypeReference<>() {
            });
            loaded.forEach(conversation -> conversations.put(conversation.getId(), conversation));
        }
    }

    private void loadMessages() throws IOException {
        Path path = file("messages.json");
        if (Files.exists(path)) {
            messages.addAll(mapper.readValue(path.toFile(), new TypeReference<List<ChatMessage>>() {
            }));
        }
    }

    private void persistAll() {
        persistDocuments();
        persistChunks();
        persistConversations();
        persistMessages();
    }

    private void persistDocuments() {
        write(file("documents.json"), findAllDocuments());
    }

    private void persistChunks() {
        write(file("chunks.json"), chunks);
    }

    private void persistConversations() {
        write(file("conversations.json"), conversations.values().stream().toList());
    }

    private void persistMessages() {
        write(file("messages.json"), messages);
    }

    private Path file(String filename) {
        return properties.storagePath().resolve(filename);
    }

    private boolean contains(String value, String normalizedKeyword) {
        return normalize(value).contains(normalizedKeyword);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String snippet(String content, String keyword) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalizedContent = normalize(content);
        String normalizedKeyword = normalize(keyword);
        int matchIndex = normalizedContent.indexOf(normalizedKeyword);
        if (matchIndex < 0) {
            return truncate(content.trim(), 120);
        }
        int start = Math.max(0, matchIndex - 45);
        int end = Math.min(content.length(), matchIndex + keyword.length() + 75);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < content.length() ? "..." : "";
        return prefix + content.substring(start, end).trim() + suffix;
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "...";
    }

    private void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
        } catch (IOException ex) {
            throw new IllegalStateException("写入本地 RAG 存储失败: " + path, ex);
        }
    }
}
