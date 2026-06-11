package com.example.system.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.system.config.RagProperties;
import com.example.system.dto.AppStatusResponse;
import com.example.system.dto.AskRequest;
import com.example.system.dto.AskResponse;
import com.example.system.dto.CreateConversationRequest;
import com.example.system.dto.HistorySearchResult;
import com.example.system.dto.UploadResponse;
import com.example.system.model.ChatMessage;
import com.example.system.model.Conversation;
import com.example.system.model.DocumentRecord;
import com.example.system.service.AnswerGeneratorService;
import com.example.system.service.ChatService;
import com.example.system.service.DocumentService;
import com.example.system.service.embedding.EmbeddingService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final RagProperties properties;
    private final DocumentService documentService;
    private final ChatService chatService;
    private final EmbeddingService embeddingService;
    private final AnswerGeneratorService answerGeneratorService;

    public ApiController(RagProperties properties, DocumentService documentService, ChatService chatService,
            EmbeddingService embeddingService, AnswerGeneratorService answerGeneratorService) {
        this.properties = properties;
        this.documentService = documentService;
        this.chatService = chatService;
        this.embeddingService = embeddingService;
        this.answerGeneratorService = answerGeneratorService;
    }

    @GetMapping("/status")
    public AppStatusResponse status() {
        return new AppStatusResponse(
                embeddingService.mode(),
                answerGeneratorService.mode(),
                properties.getChunkSize(),
                properties.getChunkOverlap(),
                properties.getTopK());
    }

    @GetMapping("/documents")
    public List<DocumentRecord> documents() {
        return documentService.findAllDocuments();
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        return documentService.upload(file);
    }

    @DeleteMapping("/documents/{documentId}")
    public void deleteDocument(@PathVariable String documentId) {
        documentService.deleteDocument(documentId);
    }

    @GetMapping("/documents/{documentId}/conversations")
    public List<Conversation> conversations(@PathVariable String documentId) {
        return documentService.findConversations(documentId);
    }

    @PostMapping("/documents/{documentId}/conversations")
    public Conversation createConversation(@PathVariable String documentId,
            @RequestBody(required = false) CreateConversationRequest request) {
        String title = request == null ? "新会话" : request.title();
        return documentService.createConversation(documentId, title);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<ChatMessage> messages(@PathVariable String conversationId) {
        return chatService.findMessages(conversationId);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public AskResponse ask(@PathVariable String conversationId, @RequestBody AskRequest request) {
        return chatService.ask(
                conversationId,
                request == null ? null : request.question(),
                request == null ? null : request.topK());
    }

    @GetMapping("/history/search")
    public List<HistorySearchResult> searchHistory(@RequestParam("keyword") String keyword) {
        return chatService.searchHistory(keyword);
    }
}
