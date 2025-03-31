package com.example.globalTimes_be.domain.news.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "상세 뉴스 페이지 응답 DTO")
public class NewsDetailDTO {
    @Schema(description = "언론사명", example = "BBC News")
    private String sourceName;

    @Schema(description = "작성자", example = "Gordon Gottsegen")
    private String author;

    @Schema(description = "제목", example = "Trump Auto Tariffs: Car Import...")
    private String title;

    @Schema(description = "조회수", example = "1")
    private Long viewCount;

    @Schema(description = "원본기사 url", example = "https://www.bbc.com/news...")
    private String url;

    @Schema(description = "기사관련 이미지", example = "https://ichef.bbci~~.jpg")
    private String urlToImage;

    @Schema(description = "작성일", example = "2025-03-27T19:52:00Z")
    private LocalDateTime publishedAt;
}
