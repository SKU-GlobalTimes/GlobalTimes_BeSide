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
import org.springframework.dao.DataIntegrityViolationException;
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
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1', '2', '3') AND success = 1",
                Integer.class
        );
        Integer domainTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() " +
                        "AND table_name IN ('source', 'users', 'article', 'scrap', 'chat_history')",
                Integer.class
        );
        List<String> fullTextColumns = fullTextColumns(jdbcTemplate);

        assertThat(migrationCount).isEqualTo(3);
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

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
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

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(normalizationProcedureCount(legacyJdbcTemplate)).isZero();
            assertThat(foreignKeyRules(legacyJdbcTemplate, "fk_article_source"))
                    .isEqualTo("NO ACTION/NO ACTION");
        } finally {
            dropDatabase(databaseName);
        }
    }

    @Test
    void flywayV3RemovesSafeDuplicatesAndRejectsFutureDuplicateUrl() throws Exception {
        String databaseName = "url_uniqueness_baseline";
        DriverManagerDataSource dataSource = createLegacyDatabase(databaseName);
        JdbcTemplate targetJdbcTemplate = new JdbcTemplate(dataSource);

        try {
            flywayAtVersion2(dataSource).migrate();
            long sourceId = insertSource(targetJdbcTemplate, "URL Unique Source");
            insertArticle(targetJdbcTemplate, sourceId, "older title", "https://news.example/duplicate", null);
            insertArticle(targetJdbcTemplate, sourceId, "latest title", "https://news.example/duplicate", null);
            insertArticle(targetJdbcTemplate, sourceId, "other title", "https://news.example/other", null);
            insertArticle(targetJdbcTemplate, sourceId, "upper path", "https://news.example/Path", null);
            insertArticle(targetJdbcTemplate, sourceId, "lower path", "https://news.example/path", null);

            assertThat(baselineFlyway(dataSource).migrate().migrationsExecuted).isEqualTo(1);
            assertThat(articleCountByExactUrl(targetJdbcTemplate, "https://news.example/duplicate")).isEqualTo(1);
            assertThat(targetJdbcTemplate.queryForObject(
                    "SELECT title FROM article WHERE url = ?",
                    String.class,
                    "https://news.example/duplicate"
            )).isEqualTo("latest title");
            assertThat(articleCountByExactUrl(targetJdbcTemplate, "https://news.example/Path")).isEqualTo(1);
            assertThat(articleCountByExactUrl(targetJdbcTemplate, "https://news.example/path")).isEqualTo(1);
            assertThat(targetJdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT HEX(url_hash)) FROM article " +
                            "WHERE LOWER(url) = 'https://news.example/path'",
                    Integer.class
            )).isEqualTo(2);
            assertThat(urlHashIndexColumns(targetJdbcTemplate)).containsExactly("url_hash");

            assertThatThrownBy(() -> insertArticle(
                    targetJdbcTemplate,
                    sourceId,
                    "duplicate after V3",
                    "https://news.example/duplicate",
                    null
            )).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            dropDatabase(databaseName);
        }
    }

    @Test
    void flywayV3RejectsDuplicateThatContainsPreservedData() throws Exception {
        String databaseName = "unsafe_url_duplicate_baseline";
        DriverManagerDataSource dataSource = createLegacyDatabase(databaseName);
        JdbcTemplate targetJdbcTemplate = new JdbcTemplate(dataSource);

        try {
            flywayAtVersion2(dataSource).migrate();
            long sourceId = insertSource(targetJdbcTemplate, "Unsafe Duplicate Source");
            insertArticle(
                    targetJdbcTemplate,
                    sourceId,
                    "older enriched title",
                    "https://news.example/unsafe-duplicate",
                    "summary to preserve"
            );
            insertArticle(
                    targetJdbcTemplate,
                    sourceId,
                    "latest title",
                    "https://news.example/unsafe-duplicate",
                    null
            );
            long protectedArticleId = targetJdbcTemplate.queryForObject(
                    "SELECT MIN(article_id) FROM article WHERE url = ?",
                    Long.class,
                    "https://news.example/unsafe-duplicate"
            );
            long userId = insertUser(targetJdbcTemplate, "unsafe-duplicate-user@example.com");
            targetJdbcTemplate.update(
                    "INSERT INTO scrap(created_at, article_id, user_id) VALUES (NOW(6), ?, ?)",
                    protectedArticleId,
                    userId
            );

            Flyway flyway = baselineFlyway(dataSource);

            assertThatThrownBy(flyway::migrate)
                    .isInstanceOf(FlywayException.class)
                    .hasRootCauseMessage(
                            "Unsafe duplicate article data must be resolved before URL uniqueness migration"
                    );
            assertThat(articleCountByUrl(
                    targetJdbcTemplate,
                    "https://news.example/unsafe-duplicate"
            )).isEqualTo(2);
            assertThat(targetJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM scrap",
                    Integer.class
            )).isEqualTo(1);

            targetJdbcTemplate.update("DELETE FROM scrap");
            targetJdbcTemplate.update(
                    "UPDATE article SET summary = NULL WHERE article_id = ?",
                    protectedArticleId
            );
            flyway.repair();

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(articleCountByExactUrl(
                    targetJdbcTemplate,
                    "https://news.example/unsafe-duplicate"
            )).isEqualTo(1);
            assertThat(urlUniquenessProcedureCount(targetJdbcTemplate)).isZero();
        } finally {
            dropDatabase(databaseName);
        }
    }

    @Test
    void flywayV3ValidatesCanonicalColumnBeforeDeletingDuplicatesAndCanRetry() throws Exception {
        String databaseName = "invalid_url_hash_baseline";
        DriverManagerDataSource dataSource = createLegacyDatabase(databaseName);
        JdbcTemplate targetJdbcTemplate = new JdbcTemplate(dataSource);

        try {
            flywayAtVersion2(dataSource).migrate();
            long sourceId = insertSource(targetJdbcTemplate, "Invalid URL Hash Source");
            insertArticle(targetJdbcTemplate, sourceId, "older title", "https://news.example/schema-error", null);
            insertArticle(targetJdbcTemplate, sourceId, "latest title", "https://news.example/schema-error", null);
            targetJdbcTemplate.execute(
                    "ALTER TABLE article ADD COLUMN url_hash BINARY(32) " +
                            "GENERATED ALWAYS AS " +
                            "(UNHEX(SHA2(CONCAT(url, 'wrong'), 256))) STORED"
            );
            Flyway flyway = baselineFlyway(dataSource);

            assertThatThrownBy(flyway::migrate)
                    .isInstanceOf(FlywayException.class)
                    .hasRootCauseMessage("article.url_hash has an unexpected definition");
            assertThat(articleCountByExactUrl(
                    targetJdbcTemplate,
                    "https://news.example/schema-error"
            )).isEqualTo(2);
            assertThat(urlUniquenessProcedureCount(targetJdbcTemplate)).isEqualTo(1);

            targetJdbcTemplate.execute("ALTER TABLE article DROP COLUMN url_hash");
            flyway.repair();

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(articleCountByExactUrl(
                    targetJdbcTemplate,
                    "https://news.example/schema-error"
            )).isEqualTo(1);
            assertThat(urlUniquenessProcedureCount(targetJdbcTemplate)).isZero();
        } finally {
            dropDatabase(databaseName);
        }
    }

    @Test
    void databaseUniqueConstraintAllowsOnlyOneConcurrentInsertPerUrl() throws Exception {
        String databaseName = "concurrent_url_uniqueness";
        DriverManagerDataSource dataSource = createLegacyDatabase(databaseName);
        JdbcTemplate targetJdbcTemplate = new JdbcTemplate(dataSource);

        try {
            baselineFlyway(dataSource).migrate();
            long sourceId = insertSource(targetJdbcTemplate, "Concurrent URL Source");
            String url = "https://news.example/concurrent";
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> inserts = new ArrayList<>();

            try {
                for (int i = 0; i < 2; i++) {
                    int sequence = i;
                    inserts.add(executor.submit(() -> {
                        start.await();
                        try {
                            insertArticle(targetJdbcTemplate, sourceId, "title " + sequence, url, null);
                            return true;
                        } catch (DataIntegrityViolationException e) {
                            return false;
                        }
                    }));
                }
                start.countDown();

                List<Boolean> results = new ArrayList<>();
                for (Future<Boolean> insert : inserts) {
                    results.add(insert.get(30, TimeUnit.SECONDS));
                }
                assertThat(results).containsExactlyInAnyOrder(true, false);
            } finally {
                executor.shutdownNow();
            }

            assertThat(articleCountByExactUrl(targetJdbcTemplate, url)).isEqualTo(1);
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

    private Flyway flywayAtVersion2(DriverManagerDataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .target("2")
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

    private long insertSource(JdbcTemplate targetJdbcTemplate, String sourceName) {
        targetJdbcTemplate.update(
                "INSERT INTO source(source_api_id, source_name) VALUES (NULL, ?)",
                sourceName
        );
        return targetJdbcTemplate.queryForObject(
                "SELECT source_id FROM source WHERE source_name = ?",
                Long.class,
                sourceName
        );
    }

    private void insertArticle(
            JdbcTemplate targetJdbcTemplate,
            long sourceId,
            String title,
            String url,
            String summary
    ) {
        targetJdbcTemplate.update(
                "INSERT INTO article(" +
                        "author, category, content, country, crawled_content, description, " +
                        "published_at, summary, title, url, url_to_image, view_count, source_id, language" +
                        ") VALUES (?, ?, ?, ?, NULL, ?, NOW(6), ?, ?, ?, ?, 0, ?, ?)",
                "author",
                "general",
                "content",
                "us",
                "description",
                summary,
                title,
                url,
                "https://news.example/image.jpg",
                sourceId,
                "en"
        );
    }

    private int articleCountByUrl(JdbcTemplate targetJdbcTemplate, String url) {
        Integer count = targetJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM article WHERE url = ?",
                Integer.class,
                url
        );
        return count == null ? 0 : count;
    }

    private int articleCountByExactUrl(JdbcTemplate targetJdbcTemplate, String url) {
        Integer count = targetJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM article WHERE BINARY url = BINARY ?",
                Integer.class,
                url
        );
        return count == null ? 0 : count;
    }

    private long insertUser(JdbcTemplate targetJdbcTemplate, String email) {
        targetJdbcTemplate.update(
                "INSERT INTO users(created_at, email, nickname, provider, provider_id) " +
                        "VALUES (NOW(6), ?, ?, ?, ?)",
                email,
                "nickname",
                "test",
                email
        );
        Long userId = targetJdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE email = ?",
                Long.class,
                email
        );
        return userId == null ? 0L : userId;
    }

    private int urlUniquenessProcedureCount(JdbcTemplate targetJdbcTemplate) {
        Integer count = targetJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.routines " +
                        "WHERE routine_schema = DATABASE() " +
                        "AND routine_name = 'enforce_article_url_uniqueness'",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private List<String> urlHashIndexColumns(JdbcTemplate targetJdbcTemplate) {
        return targetJdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.statistics " +
                        "WHERE table_schema = DATABASE() AND table_name = 'article' " +
                        "AND index_name = 'uk_article_url_hash' AND non_unique = 0 " +
                        "ORDER BY seq_in_index",
                String.class
        );
    }
}
