package com.example.globalTimes_be.domain.scrap.dto.response;

import com.example.globalTimes_be.domain.scrap.entity.Scrap;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
@Schema(description = "내 스크랩 목록 응답 DTO")
public class ScrapListResDTO {

    @Schema(description = "기사 ID", example = "7285")
    private Long articleId;

    @Schema(description = "기사 제목", example = "Trump Auto Tariffs...")
    private String title;

    @Schema(description = "언론사명", example = "BBC News")
    private String sourceName;

    @Schema(description = "기사 썸네일 URL", example = "https://img.example.com/photo.jpg")
    private String urlToImage;

    @Schema(description = "기사 설명", example = "Article description...")
    private String description;

    @Schema(description = "기사 발행일", example = "2025-03-30T08:37:33")
    private LocalDateTime publishedAt;

    @Schema(description = "스크랩 등록일", example = "2026-03-31T17:14:35")
    private LocalDateTime scrappedAt;

    public static ScrapListResDTO from(Scrap scrap) {
        return from(
                scrap.getArticle().getId(),
                scrap.getArticle().getTitle(),
                scrap.getArticle().getSource().getSourceName(),
                scrap.getArticle().getUrlToImage(),
                scrap.getArticle().getDescription(),
                scrap.getArticle().getPublishedAt(),
                scrap.getCreatedAt()
        );
    }

    public static ScrapListResDTO from(
            Long articleId,
            String title,
            String sourceName,
            String urlToImage,
            String description,
            LocalDateTime publishedAt,
            LocalDateTime scrappedAt
    ) {
        return ScrapListResDTO.builder()
                .articleId(articleId)
                .title(title)
                .sourceName(sourceName)
                .urlToImage(urlToImage)
                .description(description)
                .publishedAt(publishedAt)
                .scrappedAt(scrappedAt)
                .build();
    }
}
