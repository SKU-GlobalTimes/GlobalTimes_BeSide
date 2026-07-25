package com.example.globalTimes_be.domain.chat.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ChatHistoryService.class)
class ChatHistorySaveIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withUsername("root")
            .withPassword("test");

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long userId;
    private long articleId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO source(source_api_id, source_name) VALUES (?, ?)",
                "chat-save-source-id",
                "Chat Save Source"
        );
        long sourceId = jdbcTemplate.queryForObject(
                "SELECT source_id FROM source WHERE source_name = ?",
                Long.class,
                "Chat Save Source"
        );

        jdbcTemplate.update(
                "INSERT INTO users(created_at, email, nickname, provider, provider_id) " +
                        "VALUES (NOW(6), ?, ?, ?, ?)",
                "chat-save@example.com",
                "chat-save-user",
                "test",
                "chat-save-provider-id"
        );
        userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE email = ?",
                Long.class,
                "chat-save@example.com"
        );

        jdbcTemplate.update(
                "INSERT INTO article(author, category, content, country, description, published_at, " +
                        "title, url, url_to_image, view_count, source_id, language) " +
                        "VALUES (?, ?, ?, ?, ?, NOW(6), ?, ?, ?, ?, ?, ?)",
                "author",
                "general",
                "content",
                "us",
                "description",
                "Chat save article",
                "https://news.example/chat-save",
                "https://news.example/chat-save.jpg",
                0L,
                sourceId,
                "en"
        );
        articleId = jdbcTemplate.queryForObject(
                "SELECT article_id FROM article WHERE url = ?",
                Long.class,
                "https://news.example/chat-save"
        );
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM chat_history");
        jdbcTemplate.update("DELETE FROM article");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM source");
    }

    @Test
    void savesCompletedLoginTurn() {
        chatHistoryService.save(userId, articleId, "question", "answer");

        assertThat(chatCount()).isEqualTo(1);
    }

    @Test
    void missingUserAndArticleFailuresReachCaller() {
        assertThatThrownBy(() ->
                chatHistoryService.save(Long.MAX_VALUE, articleId, "question", "answer")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 사용자: " + Long.MAX_VALUE);

        assertThatThrownBy(() ->
                chatHistoryService.save(userId, Long.MAX_VALUE, "question", "answer")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 기사: " + Long.MAX_VALUE);
    }

    @Test
    void databaseWriteFailureReachesCallerAndRollsBack() {
        String oversizedAnswer = "x".repeat(70_000);

        assertThatThrownBy(() ->
                chatHistoryService.save(userId, articleId, "question", oversizedAnswer)
        ).isInstanceOf(RuntimeException.class);

        assertThat(chatCount()).isZero();
    }

    private int chatCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_history WHERE user_id = ? AND article_id = ?",
                Integer.class,
                userId,
                articleId
        );
    }
}
