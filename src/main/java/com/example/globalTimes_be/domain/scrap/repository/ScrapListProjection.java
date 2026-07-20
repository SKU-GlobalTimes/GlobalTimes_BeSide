package com.example.globalTimes_be.domain.scrap.repository;

import java.time.LocalDateTime;

public interface ScrapListProjection {

    Long getArticleId();

    String getTitle();

    String getSourceName();

    String getUrlToImage();

    String getDescription();

    LocalDateTime getPublishedAt();

    LocalDateTime getScrappedAt();
}
