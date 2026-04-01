package com.example.globalTimes_be.domain.scrap.entity;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "scrap", indexes = {
        @Index(name = "idx_scrap_user_id", columnList = "user_id"),
        @Index(name = "idx_scrap_user_article", columnList = "user_id, article_id", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Scrap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scrap_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static Scrap create(User user, Article article) {
        return Scrap.builder()
                .user(user)
                .article(article)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
