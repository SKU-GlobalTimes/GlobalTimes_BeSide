package com.example.globalTimes_be.domain.article.repository;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.source.entity.Source;
import com.example.globalTimes_be.domain.source.repository.SourceRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
class ArticleRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withUsername("root")
            .withPassword("test");

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void flywayCreatesBaselineSchemaAndFullTextIndex() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1', '2') AND success = 1",
                Integer.class
        );
        Integer domainTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() " +
                        "AND table_name IN ('source', 'users', 'article', 'scrap', 'chat_history')",
                Integer.class
        );
        List<String> fullTextColumns = fullTextColumns(jdbcTemplate);

        assertThat(migrationCount).isEqualTo(2);
        assertThat(domainTableCount).isEqualTo(5);
        assertThat(fullTextColumns).containsExactly("title", "description");
    }

    @Test
    void flywayNormalizesLegacyBaselineAndRestoresMissingFullTextIndex() throws Exception {
        String databaseName = "legacy_baseline";
        DriverManagerDataSource legacyDataSource = createLegacyDatabase(databaseName);
        JdbcTemplate legacyJdbcTemplate = new JdbcTemplate(legacyDataSource);

        try {
            baselineFlyway(legacyDataSource).migrate();

            assertThat(schemaObjectNames(legacyJdbcTemplate)).containsExactlyInAnyOrder(
                    "fk_article_source",
                    "fk_chat_article",
                    "fk_chat_user",
                    "fk_scrap_article",
                    "fk_scrap_user",
                    "ft_article_title_description",
                    "idx_article_source_id",
                    "idx_chat_article_id",
                    "idx_scrap_article_id",
                    "uk_source_name"
            );
            assertThat(fullTextColumns(legacyJdbcTemplate)).containsExactly("title", "description");
        } finally {
            dropDatabase(databaseName);
        }
    }

    @Test
    void flywayRejectsWrongCanonicalIndexAndCanRetryAfterRepair() throws Exception {
        String databaseName = "invalid_index_baseline";
        DriverManagerDataSource legacyDataSource = createLegacyDatabase(databaseName);
        JdbcTemplate legacyJdbcTemplate = new JdbcTemplate(legacyDataSource);
        Flyway flyway = baselineFlyway(legacyDataSource);

        try {
            legacyJdbcTemplate.execute(
                    "ALTER TABLE source DROP INDEX UK3pganbnum82xyg852kf3qonwu, " +
                            "ADD INDEX uk_source_name (source_api_id)"
            );

            assertThatThrownBy(flyway::migrate)
                    .isInstanceOf(FlywayException.class)
                    .hasRootCauseMessage("Index source.uk_source_name has an unexpected definition");
            assertThat(normalizationProcedureCount(legacyJdbcTemplate)).isEqualTo(2);

            legacyJdbcTemplate.execute(
                    "ALTER TABLE source DROP INDEX uk_source_name, " +
                            "ADD UNIQUE INDEX UK3pganbnum82xyg852kf3qonwu (source_name)"
            );
            flyway.repair();

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(normalizationProcedureCount(legacyJdbcTemplate)).isZero();
            assertThat(schemaObjectNames(legacyJdbcTemplate)).contains("uk_source_name");
        } finally {
            dropDatabase(databaseName);
        }
    }

    @Test
    void flywayRejectsWrongCanonicalForeignKeyAndCanRetryAfterRepair() throws Exception {
        String databaseName = "invalid_fk_baseline";
        DriverManagerDataSource legacyDataSource = createLegacyDatabase(databaseName);
        JdbcTemplate legacyJdbcTemplate = new JdbcTemplate(legacyDataSource);
        Flyway flyway = baselineFlyway(legacyDataSource);

        try {
            legacyJdbcTemplate.execute(
                    "ALTER TABLE article DROP FOREIGN KEY FK3ltucw25icv8f9x6ek63mmtkj, " +
                            "ADD CONSTRAINT fk_article_source FOREIGN KEY (source_id) " +
                            "REFERENCES source (source_id) ON DELETE CASCADE"
            );

            assertThatThrownBy(flyway::migrate)
                    .isInstanceOf(FlywayException.class)
                    .hasRootCauseMessage("Foreign key article.fk_article_source has an unexpected definition");
            assertThat(normalizationProcedureCount(legacyJdbcTemplate)).isEqualTo(2);

            legacyJdbcTemplate.execute(
                    "ALTER TABLE article DROP FOREIGN KEY fk_article_source, " +
                            "ADD CONSTRAINT FK3ltucw25icv8f9x6ek63mmtkj FOREIGN KEY (source_id) " +
                            "REFERENCES source (source_id)"
            );
            flyway.repair();

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(normalizationProcedureCount(legacyJdbcTemplate)).isZero();
            assertThat(foreignKeyRules(legacyJdbcTemplate, "fk_article_source"))
                    .isEqualTo("NO ACTION/NO ACTION");
        } finally {
            dropDatabase(databaseName);
        }
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

    private String jdbcUrlFor(String databaseName) {
        return MYSQL.getJdbcUrl().replaceFirst("/[^/?]+(\\?.*)?$", "/" + databaseName + "$1");
    }

    private DriverManagerDataSource createLegacyDatabase(String databaseName) throws Exception {
        dropDatabase(databaseName);
        jdbcTemplate.execute("CREATE DATABASE " + databaseName +
                " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                jdbcUrlFor(databaseName),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        );
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new org.springframework.core.io.ClassPathResource("db/legacy/baseline_schema.sql")
            );
        }
        return dataSource;
    }

    private Flyway baselineFlyway(DriverManagerDataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
    }

    private void dropDatabase(String databaseName) {
        jdbcTemplate.execute("DROP DATABASE IF EXISTS " + databaseName);
    }

    private List<String> fullTextColumns(JdbcTemplate targetJdbcTemplate) {
        return targetJdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.statistics " +
                        "WHERE table_schema = DATABASE() " +
                        "AND table_name = 'article' " +
                        "AND index_name = 'ft_article_title_description' " +
                        "AND index_type = 'FULLTEXT' " +
                        "ORDER BY seq_in_index",
                String.class
        );
    }

    private List<String> schemaObjectNames(JdbcTemplate targetJdbcTemplate) {
        List<String> names = new ArrayList<>();
        names.addAll(targetJdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.key_column_usage " +
                        "WHERE constraint_schema = DATABASE() AND referenced_table_name IS NOT NULL",
                String.class
        ));
        names.addAll(targetJdbcTemplate.queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics " +
                        "WHERE table_schema = DATABASE() AND index_name IN " +
                        "('uk_source_name', 'idx_article_source_id', 'idx_scrap_article_id', " +
                        "'idx_chat_article_id', 'ft_article_title_description')",
                String.class
        ));
        return names;
    }

    private int normalizationProcedureCount(JdbcTemplate targetJdbcTemplate) {
        Integer count = targetJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.routines " +
                        "WHERE routine_schema = DATABASE() " +
                        "AND routine_name IN ('normalize_index', 'normalize_foreign_key')",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private String foreignKeyRules(
            JdbcTemplate targetJdbcTemplate,
            String constraintName
    ) {
        return targetJdbcTemplate.queryForObject(
                "SELECT CONCAT(update_rule, '/', delete_rule) " +
                        "FROM information_schema.referential_constraints " +
                        "WHERE constraint_schema = DATABASE() AND constraint_name = ?",
                String.class,
                constraintName
        );
    }
}
