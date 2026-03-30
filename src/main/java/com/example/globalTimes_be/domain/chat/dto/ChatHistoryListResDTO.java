package com.example.globalTimes_be.domain.chat.dto;

import com.example.globalTimes_be.domain.chat.entity.ChatHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 플로팅 팝업 목록 - 기사별 마지막 대화 미리보기 (1건)
@Getter
@Builder
public class ChatHistoryListResDTO {

    private Long articleId;
    private String articleTitle;
    private String thumbnailUrl;
    private String lastQuestion;
    private String lastAnswerPreview; // 최대 100자 미리보기
    private LocalDateTime lastChatAt;

    private static final int PREVIEW_MAX_LENGTH = 100;

    public static ChatHistoryListResDTO from(ChatHistory latestChat) {
        String answer = latestChat.getAnswer();
        String preview = answer.length() > PREVIEW_MAX_LENGTH
                ? answer.substring(0, PREVIEW_MAX_LENGTH) + "..."
                : answer;

        return ChatHistoryListResDTO.builder()
                .articleId(latestChat.getArticle().getId())
                .articleTitle(latestChat.getArticle().getTitle())
                .thumbnailUrl(latestChat.getArticle().getUrlToImage())
                .lastQuestion(latestChat.getQuestion())
                .lastAnswerPreview(preview)
                .lastChatAt(latestChat.getCreatedAt())
                .build();
    }
}
