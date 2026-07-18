package com.example.globalTimes_be.domain.article.repository;

import java.time.LocalDateTime;

public interface ArticleSummaryProjection {

    Long getId();

    String getSourceName();

    String getTitle();

    String getDescription();

    String getUrlToImage();

    LocalDateTime getPublishedAt();
}
