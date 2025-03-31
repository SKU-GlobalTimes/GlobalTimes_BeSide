package com.example.globalTimes_be.domain.news.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(name = "author")
    private String author;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "crawled_content", columnDefinition = "TEXT")
    private String crawledContent;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "url_to_image", nullable = false)
    private String urlToImage;

    @Column(name = "published_at", nullable = false)
    private String publishedAt;

    public void setCrawledContent(String crawledContent){
        this.crawledContent = crawledContent;
    }
}
