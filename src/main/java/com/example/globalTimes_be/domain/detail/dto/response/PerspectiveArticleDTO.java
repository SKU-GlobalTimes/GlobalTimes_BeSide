package com.example.globalTimes_be.domain.detail.dto.response;

import com.example.globalTimes_be.domain.article.entity.Article;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PerspectiveArticleDTO {

    private Long id;
    private String country;
    private String language;
    private String source;
    private String title;
    private String description;
    private String urlToImage;
    private LocalDateTime publishedAt;

    public static PerspectiveArticleDTO fromEntity(Article article) {
        return PerspectiveArticleDTO.builder()
                .id(article.getId())
                .country(article.getCountry())
                .language(article.getLanguage())
                .source(article.getSource().getSourceName())
                .title(article.getTitle())
                .description(article.getDescription())
                .urlToImage(article.getUrlToImage())
                .publishedAt(article.getPublishedAt())
                .build();
    }
}
