package com.example.globalTimes_be.domain.article.repository;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.source.entity.Source;
import com.example.globalTimes_be.domain.source.repository.SourceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ArticleRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            articleRepository.deleteAll();
            sourceRepository.deleteAll();
        });
    }

    @Test
    void atomicIncrementKeepsAllConcurrentUpdates() throws Exception {
        Long articleId = createArticle();
        int requestCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> updates = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                updates.add(executor.submit(() -> {
                    start.await();
                    return transactionTemplate.execute(
                            status -> articleRepository.incrementViewCount(articleId)
                    );
                }));
            }

            start.countDown();

            for (Future<Integer> update : updates) {
                assertThat(update.get(30, TimeUnit.SECONDS)).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(currentViewCount(articleId)).isEqualTo(requestCount);
    }

    @Test
    void atomicIncrementRollsBackWhenTransactionFails() {
        Long articleId = createArticle();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            articleRepository.incrementViewCount(articleId);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(currentViewCount(articleId)).isZero();
    }

    private Long createArticle() {
        return transactionTemplate.execute(status -> {
            Source source = sourceRepository.saveAndFlush(Source.createSource("Integration Source", null));
            Article article = Article.createArticle(
                    source,
                    "author",
                    "title",
                    "description",
                    "content",
                    "https://news.example/integration",
                    "https://news.example/image.jpg",
                    "2026-07-15T10:00:00Z",
                    "us",
                    "general"
            );
            return articleRepository.saveAndFlush(article).getId();
        });
    }

    private long currentViewCount(Long articleId) {
        return transactionTemplate.execute(status -> articleRepository.findById(articleId)
                .orElseThrow()
                .getViewCount());
    }
}
