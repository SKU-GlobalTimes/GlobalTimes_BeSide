package com.example.globalTimes_be.domain.chat.service;

import com.example.globalTimes_be.domain.chat.dto.ChatHistoryListResDTO;
import com.example.globalTimes_be.domain.chat.entity.ChatHistory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ChatHistoryService.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
class ChatHistoryQueryIntegrationTest {

    private static final int ARTICLE_COUNT = 100;
    private static final int CHATS_PER_ARTICLE = 50;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withUsername("root")
            .withPassword("test");

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long userId;
    private Statistics statistics;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUpFixture() {
        long sourceId = insertSource();
        userId = insertUser();
        List<Long> articleIds = insertArticles(sourceId);
        insertChats(articleIds, userId);
        insertOtherUserChat(articleIds.get(0));

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setReadOnly(true);
        entityManager.clear();
        statistics.clear();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM chat_history");
        jdbcTemplate.update("DELETE FROM article");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM source");
    }

    @Test
    void latestPerArticleQueryReducesLoadedRowsAndStatements() {
        long baselineStartedAt = System.nanoTime();

        List<ChatHistoryListResDTO> baseline = transactionTemplate.execute(status -> baselineChatList());

        long baselineElapsedMs = (System.nanoTime() - baselineStartedAt) / 1_000_000;
        System.out.printf(
                "CHAT_LIST_BASELINE histories=%d, result=%d, statements=%d, entities=%d, elapsedMs=%d%n",
                ARTICLE_COUNT * CHATS_PER_ARTICLE,
                baseline.size(),
                statistics.getPrepareStatementCount(),
                statistics.getEntityLoadCount(),
                baselineElapsedMs
        );

        assertThat(baseline).hasSize(ARTICLE_COUNT);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(ARTICLE_COUNT + 1L);
        assertThat(statistics.getEntityLoadCount())
                .isEqualTo(ARTICLE_COUNT * CHATS_PER_ARTICLE + ARTICLE_COUNT);

        entityManager.clear();
        statistics.clear();
        long optimizedStartedAt = System.nanoTime();

        List<ChatHistoryListResDTO> optimized = chatHistoryService.getChatList(userId);

        long optimizedElapsedMs = (System.nanoTime() - optimizedStartedAt) / 1_000_000;
        System.out.printf(
                "CHAT_LIST_OPTIMIZED histories=%d, result=%d, statements=%d, entities=%d, elapsedMs=%d%n",
                ARTICLE_COUNT * CHATS_PER_ARTICLE,
                optimized.size(),
                statistics.getPrepareStatementCount(),
                statistics.getEntityLoadCount(),
                optimizedElapsedMs
        );

        assertThat(optimized).hasSize(ARTICLE_COUNT);
        assertThat(optimized).extracting(ChatHistoryListResDTO::getArticleId)
                .containsExactlyElementsOf(baseline.stream()
                        .map(ChatHistoryListResDTO::getArticleId)
                        .toList());
        assertThat(optimized).allSatisfy(dto -> assertThat(dto.getLastQuestion()).endsWith("-49"));
        assertThat(optimized.get(0).getLastAnswerPreview())
                .isEqualTo("x".repeat(100) + "...");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1L);
        assertThat(statistics.getEntityLoadCount()).isZero();

        String explainPlan = jdbcTemplate.queryForObject(
                "EXPLAIN ANALYZE " +
                        "SELECT ranked.chat_id " +
                        "FROM (" +
                        "SELECT chat.chat_id, chat.article_id, chat.created_at, " +
                        "ROW_NUMBER() OVER (" +
                        "PARTITION BY chat.article_id " +
                        "ORDER BY chat.created_at DESC, chat.chat_id DESC" +
                        ") rn " +
                        "FROM chat_history chat WHERE chat.user_id = ?" +
                        ") ranked " +
                        "JOIN article ON article.article_id = ranked.article_id " +
                        "WHERE ranked.rn = 1 " +
                        "ORDER BY ranked.created_at DESC, ranked.chat_id DESC",
                String.class,
                userId
        );
        System.out.println("CHAT_LIST_OPTIMIZED_PLAN " + explainPlan);
    }

    private List<ChatHistoryListResDTO> baselineChatList() {
        List<ChatHistory> all = entityManager.createQuery(
                        "SELECT chat FROM ChatHistory chat " +
                                "WHERE chat.user.id = :userId ORDER BY chat.createdAt DESC",
                        ChatHistory.class
                )
                .setParameter("userId", userId)
                .getResultList();

        Map<Long, ChatHistory> latestPerArticle = new LinkedHashMap<>();
        for (ChatHistory chat : all) {
            latestPerArticle.putIfAbsent(chat.getArticle().getId(), chat);
        }
        return latestPerArticle.values().stream()
                .map(ChatHistoryListResDTO::from)
                .toList();
    }

    private long insertSource() {
        jdbcTemplate.update(
                "INSERT INTO source(source_name, source_api_id) VALUES (?, ?)",
                "Chat Query Source",
                "chat-query-source"
        );
        return jdbcTemplate.queryForObject(
                "SELECT source_id FROM source WHERE source_name = ?",
                Long.class,
                "Chat Query Source"
        );
    }

    private long insertUser() {
        jdbcTemplate.update(
                "INSERT INTO users(created_at, email, nickname, provider, provider_id) " +
                        "VALUES (NOW(6), ?, ?, ?, ?)",
                "chat-query@example.com",
                "chat-query-user",
                "test",
                "chat-query-provider-id"
        );
        return jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE email = ?",
                Long.class,
                "chat-query@example.com"
        );
    }

    private List<Long> insertArticles(long sourceId) {
        List<Object[]> parameters = new ArrayList<>();
        for (int index = 0; index < ARTICLE_COUNT; index++) {
            parameters.add(new Object[]{
                    "author",
                    "general",
                    "content",
                    "us",
                    "description",
                    Timestamp.valueOf(LocalDateTime.of(2026, 7, 19, 0, 0).plusMinutes(index)),
                    "Article " + index,
                    "https://news.example/chat-query/" + index,
                    "https://news.example/image/" + index + ".jpg",
                    sourceId,
                    "en"
            });
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO article(author, category, content, country, description, published_at, title, url, " +
                        "url_to_image, view_count, source_id, language) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                parameters
        );
        return jdbcTemplate.queryForList(
                "SELECT article_id FROM article WHERE source_id = ? ORDER BY article_id",
                Long.class,
                sourceId
        );
    }

    private void insertChats(List<Long> articleIds, long targetUserId) {
        List<Object[]> parameters = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 19, 1, 0);
        for (int articleIndex = 0; articleIndex < articleIds.size(); articleIndex++) {
            for (int chatIndex = 0; chatIndex < CHATS_PER_ARTICLE; chatIndex++) {
                int timeSequence = chatIndex == CHATS_PER_ARTICLE - 1 ? chatIndex - 1 : chatIndex;
                String answer = articleIndex == ARTICLE_COUNT - 1 && chatIndex == CHATS_PER_ARTICLE - 1
                        ? "x".repeat(120)
                        : "answer " + articleIndex + "-" + chatIndex;
                parameters.add(new Object[]{
                        answer,
                        Timestamp.valueOf(baseTime.plusMinutes(articleIndex).plusSeconds(timeSequence)),
                        "question " + articleIndex + "-" + chatIndex,
                        articleIds.get(articleIndex),
                        targetUserId
                });
            }
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO chat_history(answer, created_at, question, article_id, user_id) " +
                        "VALUES (?, ?, ?, ?, ?)",
                parameters
        );
    }

    private void insertOtherUserChat(long articleId) {
        jdbcTemplate.update(
                "INSERT INTO users(created_at, email, nickname, provider, provider_id) " +
                        "VALUES (NOW(6), ?, ?, ?, ?)",
                "other-chat-query@example.com",
                "other-chat-query-user",
                "test",
                "other-chat-query-provider-id"
        );
        Long otherUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE email = ?",
                Long.class,
                "other-chat-query@example.com"
        );
        jdbcTemplate.update(
                "INSERT INTO chat_history(answer, created_at, question, article_id, user_id) " +
                        "VALUES (?, ?, ?, ?, ?)",
                "other answer",
                Timestamp.valueOf(LocalDateTime.of(2030, 1, 1, 0, 0)),
                "other question",
                articleId,
                otherUserId
        );
    }
}
