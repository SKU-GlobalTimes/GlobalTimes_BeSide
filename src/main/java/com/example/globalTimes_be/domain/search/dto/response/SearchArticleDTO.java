package com.example.globalTimes_be.domain.search.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "검색 결과에 대한 기사 데이터 응답 DTO")
public class SearchArticleDTO {
    @Schema(description = "기사 id", example = "1")
    private Long id;

    @Schema(description = "언론사명", example = "BBC News")
    private String sourceName;

    @Schema(description = "제목", example = "Trump Auto Tariffs: Car Import...")
    private String title;

    @Schema(description = "요약본", example = "Trump Auto Tariffs: Car Import...")
    private String description;

    @Schema(description = "기사관련 이미지", example = "https://ichef.bbci~~.jpg")
    private String urlToImage;

    @Schema(description = "작성일", example = "2025-03-27T19:52:00Z")
    private LocalDateTime publishedAt;

}
