package com.example.system.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.system.config.RagProperties;
import com.example.system.dto.AskResponse;
import com.example.system.dto.HistorySearchResult;
import com.example.system.model.ChatMessage;
import com.example.system.model.Conversation;
import com.example.system.repository.RagRepository;

@Service
public class ChatService {

    private final RagProperties properties;
    private final RagRepository repository;
    private final VectorSearchService vectorSearchService;
    private final AnswerGeneratorService answerGeneratorService;

    public ChatService(RagProperties properties, RagRepository repository,
            VectorSearchService vectorSearchService, AnswerGeneratorService answerGeneratorService) {
        this.properties = properties;
        this.repository = repository;
        this.vectorSearchService = vectorSearchService;
        this.answerGeneratorService = answerGeneratorService;
    }

    public AskResponse ask(String ownerUserId, String conversationId, String question, Integer topK) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("请输入问题");
        }
        Conversation conversation = findConversationForOwner(ownerUserId, conversationId);

        int actualTopK = topK == null ? properties.getTopK() : topK;
        actualTopK = Math.max(1, Math.min(6, actualTopK));

        List<ChatMessage> history = repository.findMessagesByConversation(conversationId);
        List<SearchResult> searchResults = vectorSearchService.search(conversation.getDocumentId(), question, actualTopK);
        AnswerDraft draft = answerGeneratorService.generate(question, searchResults, history);

        Instant now = Instant.now();
        ChatMessage userMessage = new ChatMessage(
                UUID.randomUUID().toString(),
                conversationId,
                "user",
                question.trim(),
                now,
                List.of());
        ChatMessage assistantMessage = new ChatMessage(
                UUID.randomUUID().toString(),
                conversationId,
                "assistant",
                draft.content(),
                Instant.now(),
                draft.citations());

        repository.appendMessages(conversationId, userMessage, assistantMessage);
        return new AskResponse(userMessage, assistantMessage, draft.citations());
    }

    public List<ChatMessage> findMessages(String ownerUserId, String conversationId) {
        findConversationForOwner(ownerUserId, conversationId);
        return repository.findMessagesByConversation(conversationId);
    }

    public List<HistorySearchResult> searchHistory(String ownerUserId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return repository.searchHistory(keyword, 20, ownerUserId);
    }

    private Conversation findConversationForOwner(String ownerUserId, String conversationId) {
        Conversation conversation = repository.findConversation(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        repository.findDocumentForOwner(conversation.getDocumentId(), ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        return conversation;
    }
}
