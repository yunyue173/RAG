package com.example.system.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ChatMessage {

    private String id;
    private String conversationId;
    private String role;
    private String content;
    private Instant createdAt;
    private List<Citation> citations = new ArrayList<>();

    public ChatMessage() {
    }

    public ChatMessage(String id, String conversationId, String role, String content,
            Instant createdAt, List<Citation> citations) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
        this.citations = citations == null ? new ArrayList<>() : citations;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<Citation> getCitations() {
        return citations;
    }

    public void setCitations(List<Citation> citations) {
        this.citations = citations == null ? new ArrayList<>() : citations;
    }
}
