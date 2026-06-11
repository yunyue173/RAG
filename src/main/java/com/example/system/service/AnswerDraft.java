package com.example.system.service;

import java.util.List;

import com.example.system.model.Citation;

public record AnswerDraft(String content, List<Citation> citations) {
}
