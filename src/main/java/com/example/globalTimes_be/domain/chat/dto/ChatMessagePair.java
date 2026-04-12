package com.example.globalTimes_be.domain.chat.dto;

import com.example.globalTimes_be.domain.chat.entity.ChatHistory;

import java.util.List;

/**
 * Gemini 멀티턴 payload 구성용 질문·답변 쌍 (로그인 DB / 익명 Redis 공통).
 */
public record ChatMessagePair(String question, String answer) {

    public static List<ChatMessagePair> fromChatHistories(List<ChatHistory> history) {
        return history.stream()
                .map(h -> new ChatMessagePair(h.getQuestion(), h.getAnswer()))
                .toList();
    }
}
