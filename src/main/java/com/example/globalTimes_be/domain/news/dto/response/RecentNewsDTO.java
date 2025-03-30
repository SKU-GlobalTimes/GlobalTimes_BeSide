package com.example.globalTimes_be.domain.news.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "최신 뉴스 데이터 DTO")
public class RecentNewsDTO {
    @Schema(description = "뉴스기사 식별 아이디", example = "1")
    private Long id;

    @Schema(description = "언론사명", example = "BBC News")
    private String sourceName;

    @Schema(description = "제목", example = "Trump Auto Tariffs: Car Import...")
    private String title;

    @Schema(description = "기사관련 이미지", example = "https://ichef.bbci~~.jpg")
    private String urlToImage;

    @Schema(description = "작성일", example = "2025-03-27T19:52:00Z")
    private String publishedAt;
}
