package com.example.globalTimes_be.domain.article.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ArticleResponseDto {

    private Long id;                // 기사 ID
    private String sourceName;      // 언론사 (sourceName)
    private LocalDateTime publishedAt; // 발행 시간
    private String title;           // 제목
    private String description;     // 본문 요약
    private String urlToImage;      // 기사 썸네일 URL
    private Long viewCount; // 조회수
}
