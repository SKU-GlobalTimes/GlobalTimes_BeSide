package com.example.globalTimes_be.domain.chat.entity;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_history", indexes = {
        @Index(name = "idx_chat_user_id", columnList = "user_id"),
        @Index(name = "idx_chat_user_article", columnList = "user_id, article_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ChatHistory create(User user, Article article, String question, String answer) {
        return ChatHistory.builder()
                .user(user)
                .article(article)
                .question(question)
                .answer(answer)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
