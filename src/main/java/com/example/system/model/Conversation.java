package com.example.system.model;

import java.time.Instant;

public class Conversation {

    private String id;
    private String documentId;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;

    public Conversation() {
    }

    public Conversation(String id, String documentId, String title, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.documentId = documentId;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
