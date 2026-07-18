package com.example.globalTimes_be.domain.chat.dto;

import com.example.globalTimes_be.domain.article.entity.Article;
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
        return from(
                latestChat.getArticle().getId(),
                latestChat.getArticle().getTitle(),
                latestChat.getArticle().getUrlToImage(),
                latestChat.getQuestion(),
                latestChat.getAnswer(),
                latestChat.getCreatedAt()
        );
    }

    public static ChatHistoryListResDTO from(
            Long articleId,
            String articleTitle,
            String thumbnailUrl,
            String lastQuestion,
            String lastAnswer,
            LocalDateTime lastChatAt
    ) {
        return ChatHistoryListResDTO.builder()
                .articleId(articleId)
                .articleTitle(articleTitle)
                .thumbnailUrl(thumbnailUrl)
                .lastQuestion(lastQuestion)
                .lastAnswerPreview(preview(lastAnswer))
                .lastChatAt(lastChatAt)
                .build();
    }

    /** 비로그인 Redis 기사별 마지막 턴 → 플로팅 목록용 */
    public static ChatHistoryListResDTO fromAnonymous(
            Article article,
            String lastQuestion,
            String lastAnswer,
            LocalDateTime lastChatAt
    ) {
        return ChatHistoryListResDTO.builder()
                .articleId(article.getId())
                .articleTitle(article.getTitle())
                .thumbnailUrl(article.getUrlToImage())
                .lastQuestion(lastQuestion != null ? lastQuestion : "")
                .lastAnswerPreview(preview(lastAnswer))
                .lastChatAt(lastChatAt)
                .build();
    }

    private static String preview(String answer) {
        String safeAnswer = answer != null ? answer : "";
        return safeAnswer.length() > PREVIEW_MAX_LENGTH
                ? safeAnswer.substring(0, PREVIEW_MAX_LENGTH) + "..."
                : safeAnswer;
    }
}
