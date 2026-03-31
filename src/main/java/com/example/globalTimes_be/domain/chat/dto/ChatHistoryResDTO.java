package com.example.globalTimes_be.domain.chat.dto;

import com.example.globalTimes_be.domain.chat.entity.ChatHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 기사 상세 페이지 - 해당 기사의 대화 전체 목록 (1건)
@Getter
@Builder
public class ChatHistoryResDTO {

    private Long chatId;
    private String question;
    private String answer;
    private LocalDateTime createdAt;

    public static ChatHistoryResDTO from(ChatHistory chat) {
        return ChatHistoryResDTO.builder()
                .chatId(chat.getId())
                .question(chat.getQuestion())
                .answer(chat.getAnswer())
                .createdAt(chat.getCreatedAt())
                .build();
    }
}
