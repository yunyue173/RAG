package com.example.system.dto;

import java.util.List;

import com.example.system.model.ChatMessage;
import com.example.system.model.Citation;

public record AskResponse(ChatMessage userMessage, ChatMessage assistantMessage, List<Citation> citations) {
}
