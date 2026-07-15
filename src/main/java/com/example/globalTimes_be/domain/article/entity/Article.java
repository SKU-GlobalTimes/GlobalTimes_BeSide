package com.example.globalTimes_be.domain.article.entity;

import com.example.globalTimes_be.domain.source.entity.Source;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "article", indexes = {
        @Index(name = "idx_article_country",              columnList = "country"),
        @Index(name = "idx_article_category",             columnList = "category"),
        @Index(name = "idx_article_published_at",         columnList = "published_at"),
        @Index(name = "idx_article_country_category_date",columnList = "country, category, published_at")
})
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "article_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")  // Source 테이블의 id를 참조
    private Source source;  // sourceName과 연결되는 Source 객체

    @Column(name = "author", nullable = false)
    private String author;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description; // 본문 요약 ( NewsApi 자체 제공 )

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content; // 본문 서두 ( 일정 글자 이상 제공 X )

    @Column(name = "crawled_content", columnDefinition = "TEXT")
    private String crawledContent; // 크롤링된 기사 원문

    @Column( name = "summary", columnDefinition = "TEXT")
    private String summary; // 크롤링 이후 요약된 데이터 ( Gpt Response )

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "url_to_image", nullable = false, columnDefinition = "TEXT")
    private String urlToImage; // 원문 기사 썸네일

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt; // 발행 일자

    @Column(name = "view_count")
    private Long viewCount = 0L;

    @Column(name = "country", nullable = false)
    private String country;

    @Column(name = "category", nullable = true)
    private String category;

    // 원문 언어 코드 (ex. en, ko, fr, de, ja, ar, zh) - RSS 수집 기사에 사용
    @Column(name = "language", nullable = true)
    private String language;

    // News API용 정적 팩토리 메소드 (publishedAt: ISO 8601 String)
    public static Article createArticle(Source source, String author, String title,
                                        String description, String content,
                                        String url, String urlToImage, String publishedAt,
                                        String country, String category) {
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
        article.viewCount = 0L;
        article.country = country;
        article.category = category;
        article.language = "en";

        // ISO 8601 형식 변환
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(publishedAt);
        article.publishedAt = offsetDateTime.toLocalDateTime();

        return article;
    }

    // RSS 수집용 정적 팩토리 메소드 (publishedAt: 이미 파싱된 LocalDateTime)
    public static Article createRssArticle(Source source, String author, String title,
                                           String description, String content,
                                           String url, String urlToImage,
                                           LocalDateTime publishedAt,
                                           String country, String category, String language) {
        Article article = new Article();
        article.source = source;
        article.author = (author != null && !author.isBlank()) ? author : "Unknown";
        article.title = title;
        article.description = (description != null) ? description : "";
        article.content = (content != null && !content.isBlank()) ? content : article.description;
        article.crawledContent = null;
        article.summary = null;
        article.url = url;
        article.urlToImage = (urlToImage != null) ? urlToImage : "";
        article.viewCount = 0L;
        article.country = country;
        article.category = category;
        article.language = language;
        article.publishedAt = publishedAt;

        return article;
    }

    // Setter 지양 -> 메소드 대체
    public void updateSource(Source source) {
        this.source = source;
    }

    //크롤링한 기사 업데이트
    public void updateCrawledContent(String crawledContent) {
        this.crawledContent = crawledContent;
    }

    // GPT 요약 결과 저장
    public void updateSummary(String summary) {
        this.summary = summary;
    }

    public void updateCountryAndCategory(String countryCode, String category) {
        this.country = countryCode;
        this.category = category == null ? "general" : category;
    }
}
