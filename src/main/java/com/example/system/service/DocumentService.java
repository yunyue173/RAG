package com.example.system.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.system.config.RagProperties;
import com.example.system.dto.UploadResponse;
import com.example.system.model.Conversation;
import com.example.system.model.DocumentChunk;
import com.example.system.model.DocumentRecord;
import com.example.system.repository.RagRepository;
import com.example.system.service.embedding.EmbeddingService;

@Service
public class DocumentService {

    private final RagProperties properties;
    private final DocumentParserService parserService;
    private final TextSplitterService splitterService;
    private final EmbeddingService embeddingService;
    private final RagRepository repository;

    public DocumentService(RagProperties properties, DocumentParserService parserService,
            TextSplitterService splitterService, EmbeddingService embeddingService,
            RagRepository repository) {
        this.properties = properties;
        this.parserService = parserService;
        this.splitterService = splitterService;
        this.embeddingService = embeddingService;
        this.repository = repository;
    }

    public UploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择需要上传的 PDF 或 TXT 文件");
        }

        String text = parserService.parse(file);
        if (text.isBlank()) {
            throw new IllegalArgumentException("文档中没有解析到可用于问答的文本内容");
        }

        String documentId = UUID.randomUUID().toString();
        List<TextSlice> slices = splitterService.split(text);
        List<DocumentChunk> chunks = new ArrayList<>();
        for (int index = 0; index < slices.size(); index++) {
            TextSlice slice = slices.get(index);
            chunks.add(new DocumentChunk(
                    UUID.randomUUID().toString(),
                    documentId,
                    index + 1,
                    slice.startOffset(),
                    slice.endOffset(),
                    slice.content(),
                    embeddingService.embed(slice.content())));
        }

        String filename = file.getOriginalFilename() == null ? "未命名文档" : file.getOriginalFilename();
        DocumentRecord document = new DocumentRecord(
                documentId,
                filename,
                file.getContentType(),
                file.getSize(),
                text.length(),
                chunks.size(),
                Instant.now());
        repository.saveDocumentWithChunks(document, chunks);
        storeOriginalFile(documentId, filename, file);

        Conversation conversation = createConversation(documentId, "默认会话");
        return new UploadResponse(document, conversation);
    }

    public List<DocumentRecord> findAllDocuments() {
        return repository.findAllDocuments();
    }

    public DocumentRecord findDocument(String documentId) {
        return repository.findDocument(documentId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));
    }

    public Conversation createConversation(String documentId, String title) {
        findDocument(documentId);
        String normalizedTitle = title == null || title.isBlank()
                ? "新会话"
                : title.trim();
        Instant now = Instant.now();
        Conversation conversation = new Conversation(
                UUID.randomUUID().toString(),
                documentId,
                normalizedTitle,
                now,
                now);
        return repository.saveConversation(conversation);
    }

    public List<Conversation> findConversations(String documentId) {
        findDocument(documentId);
        return repository.findConversationsByDocument(documentId);
    }

    public void deleteDocument(String documentId) {
        repository.deleteDocument(documentId);
    }

    private void storeOriginalFile(String documentId, String filename, MultipartFile file) {
        try {
            Path uploadDir = properties.storagePath().resolve("uploads");
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(documentId + "-" + safeFilename(filename));
            Files.write(target, file.getBytes());
        } catch (IOException ex) {
            throw new IllegalStateException("保存原始文件失败", ex);
        }
    }

    private String safeFilename(String filename) {
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
