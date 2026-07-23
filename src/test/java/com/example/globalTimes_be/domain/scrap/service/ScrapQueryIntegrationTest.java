package com.example.globalTimes_be.domain.scrap.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.scrap.dto.response.ScrapListResDTO;
import com.example.globalTimes_be.domain.scrap.dto.response.ScrapResDTO;
import com.example.globalTimes_be.domain.scrap.entity.Scrap;
import com.example.globalTimes_be.global.exception.BaseException;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ScrapService.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
class ScrapQueryIntegrationTest {

    private static final int SCRAP_COUNT = 100;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withUsername("root")
            .withPassword("test");

    @Autowired
    private ScrapService scrapService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long userId;
    private List<Long> articleIds;
    private Statistics statistics;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUpFixture() {
        List<Long> sourceIds = insertSources();
        userId = insertUser();
        articleIds = insertArticles(sourceIds);
        insertScraps(userId, articleIds);

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setReadOnly(true);
        resetMeasurement();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM scrap");
        jdbcTemplate.update("DELETE FROM article");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM source");
    }

    @Test
    void batchProjectionsReduceStatementsAndPreserveResponses() {
        long authenticatedStartedAt = System.nanoTime();

        List<ScrapListResDTO> authenticatedBaseline = transactionTemplate.execute(
                status -> authenticatedBaseline()
        );

        long authenticatedElapsedMs = elapsedMs(authenticatedStartedAt);
        System.out.printf(
                "SCRAP_LIST_BASELINE type=authenticated scraps=%d, result=%d, statements=%d, entities=%d, elapsedMs=%d%n",
                SCRAP_COUNT,
                authenticatedBaseline.size(),
                statistics.getPrepareStatementCount(),
                statistics.getEntityLoadCount(),
                authenticatedElapsedMs
        );

        assertThat(authenticatedBaseline).hasSize(SCRAP_COUNT);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1L + SCRAP_COUNT * 2L);
        assertThat(statistics.getEntityLoadCount()).isEqualTo(SCRAP_COUNT * 3L);

        resetMeasurement();
        long authenticatedOptimizedStartedAt = System.nanoTime();

        List<ScrapListResDTO> authenticatedOptimized = scrapService.getScrapList(userId);

        long authenticatedOptimizedElapsedMs = elapsedMs(authenticatedOptimizedStartedAt);
        printOptimizedMeasurement(
                "authenticated",
                authenticatedOptimized.size(),
                authenticatedOptimizedElapsedMs
        );
        assertThat(authenticatedOptimized)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(authenticatedBaseline);
        assertThat(authenticatedOptimized.get(0).getArticleId()).isEqualTo(articleIds.get(SCRAP_COUNT - 1));
        assertThat(authenticatedOptimized.get(0).getSourceName()).isEqualTo("Scrap Source 99");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1L);
        assertThat(statistics.getEntityLoadCount()).isZero();
        System.out.println("SCRAP_LIST_OPTIMIZED_PLAN type=authenticated " + authenticatedPlan());

        resetMeasurement();
        long legacyStartedAt = System.nanoTime();

        List<ScrapResDTO> legacyBaseline = transactionTemplate.execute(status -> legacyBaseline(articleIds));

        long legacyElapsedMs = elapsedMs(legacyStartedAt);
        System.out.printf(
                "SCRAP_LIST_BASELINE type=legacy ids=%d, result=%d, statements=%d, entities=%d, elapsedMs=%d%n",
                articleIds.size(),
                legacyBaseline.size(),
                statistics.getPrepareStatementCount(),
                statistics.getEntityLoadCount(),
                legacyElapsedMs
        );

        assertThat(legacyBaseline).hasSize(SCRAP_COUNT);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(SCRAP_COUNT * 2L);
        assertThat(statistics.getEntityLoadCount()).isEqualTo(SCRAP_COUNT * 2L);

        resetMeasurement();
        long legacyOptimizedStartedAt = System.nanoTime();

        List<ScrapResDTO> legacyOptimized = scrapService.getScrap(articleIds);

        long legacyOptimizedElapsedMs = elapsedMs(legacyOptimizedStartedAt);
        printOptimizedMeasurement("legacy", legacyOptimized.size(), legacyOptimizedElapsedMs);
        assertThat(legacyOptimized)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(legacyBaseline);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1L);
        assertThat(statistics.getEntityLoadCount()).isZero();
        System.out.println("SCRAP_LIST_OPTIMIZED_PLAN type=legacy " + legacyPlan());
    }

    @Test
    void concurrentToggleSerializesRequestsForSameUserAndArticle() throws Exception {
        long articleId = articleIds.get(0);
        int requestCount = 20;
        jdbcTemplate.update(
                "DELETE FROM scrap WHERE user_id = ? AND article_id = ?",
                userId,
                articleId
        );

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> toggles = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                toggles.add(executor.submit(() -> {
                    start.await();
                    return scrapService.toggle(userId, articleId);
                }));
            }
            start.countDown();

            int successes = 0;
            int failures = 0;
            int added = 0;
            int canceled = 0;
            for (Future<Boolean> toggle : toggles) {
                try {
                    if (toggle.get(30, TimeUnit.SECONDS)) {
                        added++;
                    } else {
                        canceled++;
                    }
                    successes++;
                } catch (ExecutionException e) {
                    failures++;
                }
            }

            Integer finalRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM scrap WHERE user_id = ? AND article_id = ?",
                    Integer.class,
                    userId,
                    articleId
            );
            System.out.printf(
                    "SCRAP_TOGGLE_IMPROVED requests=%d, successes=%d, failures=%d, " +
                            "added=%d, canceled=%d, finalRows=%d%n",
                    requestCount,
                    successes,
                    failures,
                    added,
                    canceled,
                    finalRows
            );

            assertThat(successes).isEqualTo(requestCount);
            assertThat(failures).isZero();
            assertThat(added).isEqualTo(requestCount / 2);
            assertThat(canceled).isEqualTo(requestCount / 2);
            assertThat(finalRows).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sequentialTogglePreservesAddThenCancelBehavior() {
        long articleId = articleIds.get(0);
        jdbcTemplate.update(
                "DELETE FROM scrap WHERE user_id = ? AND article_id = ?",
                userId,
                articleId
        );

        assertThat(scrapService.toggle(userId, articleId)).isTrue();
        assertThat(scrapCount(userId, articleId)).isEqualTo(1);

        assertThat(scrapService.toggle(userId, articleId)).isFalse();
        assertThat(scrapCount(userId, articleId)).isZero();
    }

    @Test
    void differentUsersCanScrapSameArticleConcurrently() throws Exception {
        long articleId = articleIds.get(0);
        long anotherUserId = insertAdditionalUser();
        jdbcTemplate.update("DELETE FROM scrap WHERE article_id = ?", articleId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return scrapService.toggle(userId, articleId);
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return scrapService.toggle(anotherUserId, articleId);
            });

            start.countDown();

            assertThat(first.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(scrapCount(userId, articleId)).isEqualTo(1);
            assertThat(scrapCount(anotherUserId, articleId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void togglePreservesMissingUserAndArticleErrors() {
        assertThatThrownBy(() -> scrapService.toggle(Long.MAX_VALUE, articleIds.get(0)))
                .isInstanceOf(BaseException.class)
                .hasMessage("존재하지 않는 사용자입니다.");

        assertThatThrownBy(() -> scrapService.toggle(userId, Long.MAX_VALUE))
                .isInstanceOf(BaseException.class)
                .hasMessage("존재하지 않는 기사입니다.");
    }

    @Test
    void legacyBatchQueryPreservesDuplicateOrderAndSkipsMissingIds() {
        long missingId = articleIds.get(SCRAP_COUNT - 1) + 1_000L;
        List<Long> requestedIds = List.of(
                articleIds.get(4),
                missingId,
                articleIds.get(1),
                articleIds.get(4)
        );

        List<ScrapResDTO> result = scrapService.getScrap(requestedIds);

        assertThat(result).extracting(ScrapResDTO::getId)
                .containsExactly(articleIds.get(4), articleIds.get(1), articleIds.get(4));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1L);
        assertThat(statistics.getEntityLoadCount()).isZero();

        resetMeasurement();
        assertThat(scrapService.getScrap(List.of())).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void batchQueriesKeepArticleWhenSourceIsMissing() {
        long sourceLessArticleId = insertSourceLessArticle();
        jdbcTemplate.update(
                "INSERT INTO scrap(created_at, article_id, user_id) VALUES (?, ?, ?)",
                Timestamp.valueOf(LocalDateTime.of(2026, 7, 19, 3, 0)),
                sourceLessArticleId,
                userId
        );
        resetMeasurement();

        List<ScrapListResDTO> authenticated = scrapService.getScrapList(userId);
        List<ScrapResDTO> legacy = scrapService.getScrap(List.of(sourceLessArticleId));

        assertThat(authenticated.get(0).getArticleId()).isEqualTo(sourceLessArticleId);
        assertThat(authenticated.get(0).getSourceName()).isNull();
        assertThat(legacy).singleElement().satisfies(dto -> {
            assertThat(dto.getId()).isEqualTo(sourceLessArticleId);
            assertThat(dto.getSourceName()).isNull();
        });
    }

    private List<ScrapListResDTO> authenticatedBaseline() {
        return entityManager.createQuery(
                        "SELECT scrap FROM Scrap scrap " +
                                "WHERE scrap.user.id = :userId ORDER BY scrap.createdAt DESC",
                        Scrap.class
                )
                .setParameter("userId", userId)
                .getResultList()
                .stream()
                .map(ScrapListResDTO::from)
                .toList();
    }

    private List<ScrapResDTO> legacyBaseline(List<Long> requestedIds) {
        List<ScrapResDTO> result = new ArrayList<>();
        for (Long articleId : requestedIds) {
            Article article = entityManager.find(Article.class, articleId);
            if (article == null) {
                continue;
            }
            result.add(ScrapResDTO.from(
                    article.getId(),
                    article.getSource().getSourceName(),
                    article.getTitle(),
                    article.getDescription(),
                    article.getUrlToImage(),
                    article.getPublishedAt()
            ));
        }
        return result;
    }

    private void printOptimizedMeasurement(String type, int resultCount, long elapsedMs) {
        System.out.printf(
                "SCRAP_LIST_OPTIMIZED type=%s, result=%d, statements=%d, entities=%d, elapsedMs=%d%n",
                type,
                resultCount,
                statistics.getPrepareStatementCount(),
                statistics.getEntityLoadCount(),
                elapsedMs
        );
    }

    private String authenticatedPlan() {
        return jdbcTemplate.queryForObject(
                "EXPLAIN ANALYZE " +
                        "SELECT article.article_id, source.source_name " +
                        "FROM scrap " +
                        "JOIN article ON article.article_id = scrap.article_id " +
                        "LEFT JOIN source ON source.source_id = article.source_id " +
                        "WHERE scrap.user_id = ? " +
                        "ORDER BY scrap.created_at DESC, scrap.scrap_id DESC",
                String.class,
                userId
        );
    }

    private String legacyPlan() {
        String placeholders = String.join(",", Collections.nCopies(articleIds.size(), "?"));
        return jdbcTemplate.queryForObject(
                "EXPLAIN ANALYZE " +
                        "SELECT article.article_id, source.source_name " +
                        "FROM article " +
                        "LEFT JOIN source ON source.source_id = article.source_id " +
                        "WHERE article.article_id IN (" + placeholders + ")",
                String.class,
                articleIds.toArray()
        );
    }

    private List<Long> insertSources() {
        List<Object[]> parameters = new ArrayList<>();
        for (int index = 0; index < SCRAP_COUNT; index++) {
            parameters.add(new Object[]{"Scrap Source " + index, "scrap-source-" + index});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO source(source_name, source_api_id) VALUES (?, ?)",
                parameters
        );
        return jdbcTemplate.queryForList(
                "SELECT source_id FROM source WHERE source_name LIKE 'Scrap Source %' ORDER BY source_id",
                Long.class
        );
    }

    private long insertUser() {
        jdbcTemplate.update(
                "INSERT INTO users(created_at, email, nickname, provider, provider_id) VALUES (NOW(6), ?, ?, ?, ?)",
                "scrap-query@example.com",
                "scrap-query-user",
                "test",
                "scrap-query-provider-id"
        );
        return jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE email = ?",
                Long.class,
                "scrap-query@example.com"
        );
    }

    private long insertAdditionalUser() {
        jdbcTemplate.update(
                "INSERT INTO users(created_at, email, nickname, provider, provider_id) VALUES (NOW(6), ?, ?, ?, ?)",
                "scrap-query-2@example.com",
                "scrap-query-user-2",
                "test",
                "scrap-query-provider-id-2"
        );
        return jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE email = ?",
                Long.class,
                "scrap-query-2@example.com"
        );
    }

    private int scrapCount(long targetUserId, long targetArticleId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scrap WHERE user_id = ? AND article_id = ?",
                Integer.class,
                targetUserId,
                targetArticleId
        );
    }

    private List<Long> insertArticles(List<Long> sourceIds) {
        List<Object[]> parameters = new ArrayList<>();
        LocalDateTime publishedAt = LocalDateTime.of(2026, 7, 19, 1, 0);
        for (int index = 0; index < SCRAP_COUNT; index++) {
            parameters.add(new Object[]{
                    "author",
                    "general",
                    "content",
                    "us",
                    "Description " + index,
                    Timestamp.valueOf(publishedAt.plusMinutes(index)),
                    "Scrap Article " + index,
                    "https://news.example/scrap-query/" + index,
                    "https://news.example/scrap-image/" + index + ".jpg",
                    sourceIds.get(index),
                    "en"
            });
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO article(author, category, content, country, description, published_at, title, url, " +
                        "url_to_image, view_count, source_id, language) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                parameters
        );
        return jdbcTemplate.queryForList(
                "SELECT article_id FROM article WHERE url LIKE 'https://news.example/scrap-query/%' ORDER BY article_id",
                Long.class
        );
    }

    private void insertScraps(long targetUserId, List<Long> targetArticleIds) {
        List<Object[]> parameters = new ArrayList<>();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 19, 2, 0);
        for (int index = 0; index < targetArticleIds.size(); index++) {
            parameters.add(new Object[]{
                    Timestamp.valueOf(createdAt.plusSeconds(index)),
                    targetArticleIds.get(index),
                    targetUserId
            });
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO scrap(created_at, article_id, user_id) VALUES (?, ?, ?)",
                parameters
        );
    }

    private long insertSourceLessArticle() {
        jdbcTemplate.update(
                "INSERT INTO article(author, category, content, country, description, published_at, title, url, " +
                        "url_to_image, view_count, source_id, language) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, ?)",
                "author",
                "general",
                "content",
                "us",
                "Source-less description",
                Timestamp.valueOf(LocalDateTime.of(2026, 7, 19, 3, 0)),
                "Source-less article",
                "https://news.example/scrap-query/source-less",
                "https://news.example/scrap-image/source-less.jpg",
                "en"
        );
        return jdbcTemplate.queryForObject(
                "SELECT article_id FROM article WHERE url = ?",
                Long.class,
                "https://news.example/scrap-query/source-less"
        );
    }

    private void resetMeasurement() {
        entityManager.clear();
        statistics.clear();
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
