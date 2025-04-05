package com.example.globalTimes_be.domain.article.dto;

import com.example.globalTimes_be.domain.article.entity.Article;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@AllArgsConstructor
public class ArticleResponseDto {

    private Long id;                // 기사 ID
    private String sourceName;      // 언론사 (sourceName)
    private String publishedAt; // 발행 시간
    private String title;           // 제목
    private String description;     // 본문 요약
    private String urlToImage;      // 기사 썸네일 URL
    private Long viewCount; // 조회수

    public static ArticleResponseDto fromEntity(Article article) {
        return new ArticleResponseDto(
                article.getId(),
                article.getSource().getSourceName(),
                formatPublishedAt(article.getPublishedAt()), // ✅ 포맷팅 적용
                article.getTitle(),
                article.getDescription(),
                article.getUrlToImage(),
                article.getViewCount()
        );
    }

    private static String formatPublishedAt(LocalDateTime publishedAt) {
        ZonedDateTime articleTime = publishedAt.atZone(ZoneId.of("UTC")); // NewsAPI 시간
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));  // 서버 시간 기준

        Duration duration = Duration.between(articleTime, now);

        if (duration.toHours() < 24) {
            long hours = duration.toHours();
            return hours == 0 ? "Just now" : hours + " hours ago";
        } else {
            // ISO_LOCAL_DATE_TIME → 원하는 포맷으로 바꿔도 돼
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return articleTime.withZoneSameInstant(ZoneId.of("Asia/Seoul")).format(formatter);
        }
    }
}
