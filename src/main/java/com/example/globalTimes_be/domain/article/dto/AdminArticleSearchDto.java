package com.example.globalTimes_be.domain.article.dto;

import com.example.globalTimes_be.domain.article.entity.Article;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
@AllArgsConstructor
public class AdminArticleSearchDto {

    private Long id;
    private String sourceName;
    private String country;
    private String category;
    private String title;
    private String publishedAt;
    private Long viewCount;

    public static AdminArticleSearchDto fromEntity(Article article) {
        return new AdminArticleSearchDto(
                article.getId(),
                article.getSource().getSourceName(),
                article.getCountry(),
                article.getCategory(),
                article.getTitle(),
                article.getPublishedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                article.getViewCount()
        );
    }
}
