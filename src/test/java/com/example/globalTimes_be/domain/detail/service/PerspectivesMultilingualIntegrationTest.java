package com.example.globalTimes_be.domain.detail.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.detail.dto.response.PerspectiveArticleDTO;
import com.example.globalTimes_be.domain.detail.dto.response.PerspectivesResDTO;
import com.example.globalTimes_be.domain.search.service.TranslationService;
import com.example.globalTimes_be.domain.source.entity.Source;
import com.example.globalTimes_be.domain.source.repository.SourceRepository;
import com.example.globalTimes_be.global.redis.RedisUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PerspectivesMultilingualIntegrationTest {

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

    private TranslationService translationService;
    private RedisUtil redisUtil;
    private PerspectivesService perspectivesService;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        translationService = mock(TranslationService.class);
        redisUtil = mock(RedisUtil.class);
        perspectivesService = new PerspectivesService(
                articleRepository,
                translationService,
                redisUtil,
                new ObjectMapper().findAndRegisterModules()
        );
        ReflectionTestUtils.setField(perspectivesService, "cacheTtlSeconds", 0L);
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
    void translatedKeywordFindsStoredEnglishArticle() {
        Fixture fixture = persistFixture(
                article("kr", "ko", "루나리스 정상회담 공동성명 발표"),
                article("us", "en", "Lunaris Summit leaders release joint statement"),
                article("gb", "en", "European football transfer market opens")
        );
        when(translationService.translateToEnglish("루나리스 정상회담 공동성명"))
                .thenReturn("Lunaris Summit Joint Statement");

        PerspectivesResDTO response = getPerspectives(fixture.baseArticleId());

        assertThat(articleIds(response)).containsExactly(fixture.relatedArticleId());
        assertThat(response.getPerspectives()).containsOnlyKeys("us");
        assertThat(response.getCountriesFound()).isEqualTo(1);
        assertThat(response.getTotalArticles()).isEqualTo(1);
        verify(translationService).translateToEnglish("루나리스 정상회담 공동성명");
        verifyNoInteractions(redisUtil);
    }

    @Test
    void exactTranslationReturnsNoResultWhenRelatedArticleIsAbsent() {
        Fixture fixture = persistFixture(
                article("kr", "ko", "루나리스 정상회담 공동성명 발표"),
                null,
                article("gb", "en", "European football transfer market opens")
        );
        when(translationService.translateToEnglish("루나리스 정상회담 공동성명"))
                .thenReturn("Lunaris Summit Joint Statement");

        PerspectivesResDTO response = getPerspectives(fixture.baseArticleId());

        assertThat(articleIds(response)).isEmpty();
        assertThat(response.getCountriesFound()).isZero();
        assertThat(response.getTotalArticles()).isZero();
    }

    @Test
    void wrongTranslationReturnsNoResultEvenWhenRelatedArticleExists() {
        Fixture fixture = persistFixture(
                article("kr", "ko", "루나리스 정상회담 공동성명 발표"),
                article("us", "en", "Lunaris Summit leaders release joint statement"),
                null
        );
        when(translationService.translateToEnglish("루나리스 정상회담 공동성명"))
                .thenReturn("Football Transfer Market Opens");

        PerspectivesResDTO response = getPerspectives(fixture.baseArticleId());

        assertThat(articleIds(response)).isEmpty();
        assertThat(response.getCountriesFound()).isZero();
        assertThat(response.getTotalArticles()).isZero();
    }

    @Test
    void translationFailureFallsBackToOriginalFullTextKeyword() {
        Fixture fixture = persistFixture(
                article("fr", "fr", "Sommet Lunaris declaration commune"),
                article("fr", "fr", "Le Sommet Lunaris publie une declaration commune"),
                null
        );
        doThrow(new RuntimeException("translation timeout"))
                .when(translationService)
                .translateToEnglish("Sommet Lunaris declaration commune");

        PerspectivesResDTO response = getPerspectives(fixture.baseArticleId());

        assertThat(articleIds(response)).containsExactly(fixture.relatedArticleId());
        assertThat(response.getPerspectives()).containsOnlyKeys("fr");
        assertThat(response.getCountriesFound()).isEqualTo(1);
        assertThat(response.getTotalArticles()).isEqualTo(1);
    }

    @Test
    void translatedAndOriginalSearchesDoNotDuplicateSameArticle() {
        Fixture fixture = persistFixture(
                article("fr", "fr", "Sommet Lunaris declaration commune"),
                article("ca", "fr", "Lunaris Summit covers Sommet Lunaris declaration"),
                null
        );
        when(translationService.translateToEnglish("Sommet Lunaris declaration commune"))
                .thenReturn("Lunaris Summit Joint Statement");

        PerspectivesResDTO response = getPerspectives(fixture.baseArticleId());

        assertThat(articleIds(response)).containsExactly(fixture.relatedArticleId());
        assertThat(response.getPerspectives()).containsOnlyKeys("ca");
        assertThat(response.getTotalArticles()).isEqualTo(1);
    }

    private PerspectivesResDTO getPerspectives(Long articleId) {
        return transactionTemplate.execute(status -> perspectivesService.getPerspectives(articleId));
    }

    private Fixture persistFixture(Article base, Article related, Article unrelated) {
        return transactionTemplate.execute(status -> {
            Article savedBase = saveWithSource(base);
            Article savedRelated = related == null ? null : saveWithSource(related);
            if (unrelated != null) {
                saveWithSource(unrelated);
            }
            articleRepository.flush();
            return new Fixture(
                    savedBase.getId(),
                    savedRelated == null ? null : savedRelated.getId()
            );
        });
    }

    private Article saveWithSource(Article article) {
        Source source = sourceRepository.save(article.getSource());
        ReflectionTestUtils.setField(article, "source", source);
        return articleRepository.save(article);
    }

    private Article article(String country, String language, String title) {
        Source source = Source.createSource("Source " + country + " " + title, null);
        return Article.createRssArticle(
                source,
                "Reporter",
                title,
                "Description " + title,
                "Content " + title,
                "https://news.example/" + country + "/" + Integer.toUnsignedString(title.hashCode()),
                "https://image.example/" + country + ".jpg",
                LocalDateTime.of(2026, 7, 24, 12, 0),
                country,
                "general",
                language
        );
    }

    private List<Long> articleIds(PerspectivesResDTO response) {
        return response.getPerspectives().values().stream()
                .flatMap(List::stream)
                .map(PerspectiveArticleDTO::getId)
                .toList();
    }

    private record Fixture(Long baseArticleId, Long relatedArticleId) {
    }
}
