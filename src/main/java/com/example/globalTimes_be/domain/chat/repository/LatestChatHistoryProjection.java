package com.example.globalTimes_be.domain.chat.repository;

import java.time.LocalDateTime;

public interface LatestChatHistoryProjection {

    Long getArticleId();

    String getArticleTitle();

    String getThumbnailUrl();

    String getLastQuestion();

    String getLastAnswer();

    LocalDateTime getLastChatAt();
}
