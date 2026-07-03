package com.example.globalTimes_be.domain.detail.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.detail.dto.response.PerspectiveArticleDTO;
import com.example.globalTimes_be.domain.detail.dto.response.PerspectivesResDTO;
import com.example.globalTimes_be.domain.detail.util.KeywordExtractor;
import com.example.globalTimes_be.domain.search.service.TranslationService;
import com.example.globalTimes_be.domain.source.entity.Source;
import com.example.globalTimes_be.global.redis.RedisUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerspectivesServiceTest {

    private static final long CACHE_TTL_SECONDS = 3600L;

    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final TranslationService translationService = mock(TranslationService.class);
    private final RedisUtil redisUtil = mock(RedisUtil.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final PerspectivesService perspectivesService = new PerspectivesService(
            articleRepository,
            translationService,
            redisUtil,
            objectMapper
    );

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(perspectivesService, "cacheTtlSeconds", CACHE_TTL_SECONDS);
    }

    @Test
    void getPerspectives_returnsCachedResponseWithoutRepositoryLookup() throws Exception {
        PerspectivesResDTO cachedResponse = PerspectivesResDTO.builder()
                .keyword("cached keyword")
                .perspectives(Map.of("us", List.of(cachedArticleDto())))
                .countriesFound(1)
                .totalArticles(1)
                .build();
        when(redisUtil.getData("perspectives:article:1"))
                .thenReturn(objectMapper.writeValueAsString(cachedResponse));

        PerspectivesResDTO result = perspectivesService.getPerspectives(1L);

        assertThat(result.getKeyword()).isEqualTo("cached keyword");
        assertThat(result.getCountriesFound()).isEqualTo(1);
        assertThat(result.getTotalArticles()).isEqualTo(1);
        verify(articleRepository, never()).findById(1L);
        verify(articleRepository, never()).findPerspectives(eq(1L), anyString());
        verify(translationService, never()).translateToEnglish(anyString());
        verify(redisUtil, never()).setData(eq("perspectives:article:1"), anyString(), eq(CACHE_TTL_SECONDS));
    }

    @Test
    void getPerspectives_recalculatesAndStoresCacheWhenCacheMisses() {
        Article base = article(1L, "Global market shock hits banks", "us", "en");
        Article koreanPerspective = article(2L, "Global market shock in Seoul", "kr", "en");
        Article japanesePerspective = article(3L, "Global market shock in Tokyo", "jp", "en");
        when(redisUtil.getData("perspectives:article:1")).thenReturn(null);
        when(articleRepository.findById(1L)).thenReturn(Optional.of(base));
        when(articleRepository.findPerspectives(eq(1L), anyString()))
                .thenReturn(new java.util.ArrayList<>(List.of(koreanPerspective, japanesePerspective)));

        PerspectivesResDTO result = perspectivesService.getPerspectives(1L);

        assertThat(result.getKeyword()).isEqualTo("Global market shock hits");
        assertThat(result.getCountriesFound()).isEqualTo(2);
        assertThat(result.getTotalArticles()).isEqualTo(2);
        assertThat(result.getPerspectives()).containsKeys("kr", "jp");
        verify(translationService, never()).translateToEnglish(anyString());
        verify(redisUtil).setData(eq("perspectives:article:1"), anyString(), eq(CACHE_TTL_SECONDS));
    }

    @Test
    void getPerspectives_recalculatesWhenCacheReadFails() {
        Article base = article(1L, "Global market shock hits banks", "us", "en");
        when(redisUtil.getData("perspectives:article:1"))
                .thenThrow(new RuntimeException("redis down"));
        when(articleRepository.findById(1L)).thenReturn(Optional.of(base));
        when(articleRepository.findPerspectives(eq(1L), anyString()))
                .thenReturn(new java.util.ArrayList<>());

        PerspectivesResDTO result = perspectivesService.getPerspectives(1L);

        assertThat(result.getCountriesFound()).isZero();
        assertThat(result.getTotalArticles()).isZero();
        verify(articleRepository).findById(1L);
    }

    @Test
    void getPerspectives_recalculatesWhenCachedJsonCannotBeDeserialized() {
        Article base = article(1L, "Global market shock hits banks", "us", "en");
        when(redisUtil.getData("perspectives:article:1")).thenReturn("{broken-json");
        when(articleRepository.findById(1L)).thenReturn(Optional.of(base));
        when(articleRepository.findPerspectives(eq(1L), anyString()))
                .thenReturn(new java.util.ArrayList<>());

        PerspectivesResDTO result = perspectivesService.getPerspectives(1L);

        assertThat(result.getKeyword()).isEqualTo("Global market shock hits");
        verify(articleRepository).findById(1L);
    }

    @Test
    void getPerspectives_returnsResponseEvenWhenCacheWriteFails() {
        Article base = article(1L, "Global market shock hits banks", "us", "en");
        when(redisUtil.getData("perspectives:article:1")).thenReturn(null);
        when(articleRepository.findById(1L)).thenReturn(Optional.of(base));
        when(articleRepository.findPerspectives(eq(1L), anyString()))
                .thenReturn(new java.util.ArrayList<>());
        doThrow(new RuntimeException("redis write failed"))
                .when(redisUtil).setData(eq("perspectives:article:1"), anyString(), eq(CACHE_TTL_SECONDS));

        PerspectivesResDTO result = perspectivesService.getPerspectives(1L);

        assertThat(result.getCountriesFound()).isZero();
        assertThat(result.getTotalArticles()).isZero();
    }

    @Test
    void getPerspectives_usesOriginalKeywordWhenTranslationFails() {
        String title = "Korea economy crisis response";
        Article base = article(1L, title, "kr", "ko");
        String plainKeyword = KeywordExtractor.extractPlain(title);
        String booleanKeyword = KeywordExtractor.extract(title);
        when(redisUtil.getData("perspectives:article:1")).thenReturn(null);
        when(articleRepository.findById(1L)).thenReturn(Optional.of(base));
        when(translationService.translateToEnglish(plainKeyword))
                .thenThrow(new RuntimeException("translation failed"));
        when(articleRepository.findPerspectives(eq(1L), anyString()))
                .thenReturn(new java.util.ArrayList<>());

        PerspectivesResDTO result = perspectivesService.getPerspectives(1L);

        assertThat(result.getKeyword()).isEqualTo(plainKeyword);
        verify(translationService).translateToEnglish(plainKeyword);
        verify(articleRepository).findPerspectives(1L, booleanKeyword);
    }

    private PerspectiveArticleDTO cachedArticleDto() {
        return PerspectiveArticleDTO.builder()
                .id(10L)
                .country("us")
                .language("en")
                .source("Cached Source")
                .title("Cached title")
                .description("Cached description")
                .urlToImage("https://image.example/cached.jpg")
                .publishedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    private Article article(Long id, String title, String country, String language) {
        Source source = Source.createSource("Source " + country, null);
        Article article = Article.createRssArticle(
                source,
                "Reporter",
                title,
                "Description " + title,
                "Content " + title,
                "https://news.example/" + id,
                "https://image.example/" + id + ".jpg",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                country,
                "general",
                language
        );
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }
}
