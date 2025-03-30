package com.example.globalTimes_be.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "article_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")  // Source 테이블의 id를 참조
    private Source source;  // sourceName과 연결되는 Source 객체

    @Column(name = "author")
    private String author;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 본문 요약

    @Column(name = "content", columnDefinition = "TEXT")
    private String content; // 본문 서두

    @Column(name = "crawled_content", columnDefinition = "TEXT")
    private String crawledContent; // 크롤링된 기사 원문

    @Column( name = "summary", columnDefinition = "TEXT")
    private String summary; // 크롤링 이후 요약된 데이터 ( Gpt Response )

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "url_to_image", columnDefinition = "TEXT")
    private String urlToImage; // 원문 기사 썸네일

    @Column(name = "published_at")
    private LocalDateTime publishedAt; // 발행 일자

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    // 정적 팩토리 메소드
    public static Article createArticle(Source source, String author, String title,
                                        String description, String content,
                                        String url, String urlToImage, String publishedAt) {
        Article article = new Article();
        article.source = source;
        article.author = author;
        article.title = title;
        article.description = description;
        article.content = content;
        article.crawledContent = null;
        article.summary = null;
        article.url = url;
        article.urlToImage = urlToImage;
        // article.viewCount = (viewCount != null) ? viewCount : 0L;
        article.viewCount = 0L;

        // ISO 8601 형식 변환
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(publishedAt);
        article.publishedAt = offsetDateTime.toLocalDateTime();  // UTC → LocalDateTime 변환

        return article;
    }

    // Setter 지양 -> 메소드 대체
    public void updateSource(Source source) {
        this.source = source;
    }
}
