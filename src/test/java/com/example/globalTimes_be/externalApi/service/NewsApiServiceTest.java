package com.example.globalTimes_be.externalApi.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.article.service.ArticleService;
import com.example.globalTimes_be.domain.source.entity.Source;
import com.example.globalTimes_be.domain.source.repository.SourceRepository;
import com.example.globalTimes_be.domain.source.service.SourceService;
import com.example.globalTimes_be.externalApi.config.NewsFetchConfig;
import com.example.globalTimes_be.externalApi.dto.NewsApiArticleDto;
import com.example.globalTimes_be.externalApi.dto.NewsApiSourceDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NewsApiServiceTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final ArticleService articleService = mock(ArticleService.class);
    private final SourceService sourceService = mock(SourceService.class);
    private final NewsFetchConfig newsFetchConfig = mock(NewsFetchConfig.class);
    private final NewsApiService service = new NewsApiService(
            restTemplate, articleRepository, sourceRepository, articleService, sourceService, newsFetchConfig
    );

    @Test
    void processArticles_savesOneArticleForDuplicateUrlsAndSkipsExistingUrl() {
        Source source = Source.createSource("Test Source", null);
        NewsApiArticleDto first = article("https://news.example/new", "new");
        NewsApiArticleDto repeated = article("https://news.example/new", "repeated");
        NewsApiArticleDto existing = article("https://news.example/existing", "existing");
        when(articleRepository.findExistingUrls(anyList()))
                .thenReturn(Set.of("https://news.example/existing"));
        when(sourceService.preloadSources(List.of("Test Source")))
                .thenReturn(Map.of("Test Source", source));

        int saved = service.processArticles(List.of(first, repeated, existing), "us", "general");

        assertThat(saved).isEqualTo(1);
        verify(articleRepository).saveAll(argThat(articles -> urlsOf(articles)
                .equals(List.of("https://news.example/new"))));
    }

    @Test
    void processArticles_doesNotSaveWhenSameResponseIsProcessedAgain() {
        Source source = Source.createSource("Test Source", null);
        NewsApiArticleDto article = article("https://news.example/retry", "retry");
        when(articleRepository.findExistingUrls(anyList()))
                .thenReturn(Set.of(), Set.of("https://news.example/retry"));
        when(sourceService.preloadSources(List.of("Test Source")))
                .thenReturn(Map.of("Test Source", source));

        int firstSaved = service.processArticles(List.of(article), "us", "general");
        int secondSaved = service.processArticles(List.of(article), "us", "general");

        assertThat(firstSaved).isEqualTo(1);
        assertThat(secondSaved).isZero();
        verify(articleRepository).saveAll(argThat(articles -> urlsOf(articles)
                .equals(List.of("https://news.example/retry"))));
    }

    @Test
    void processArticlesWithStats_reportsCountsAndPublicationRange() {
        Source source = Source.createSource("Test Source", null);
        NewsApiArticleDto newArticle = article(
                "https://news.example/new", "new", "2026-07-15T10:00:00Z");
        NewsApiArticleDto repeated = article(
                "https://news.example/new", "repeated", "2026-07-15T11:00:00Z");
        NewsApiArticleDto existing = article(
                "https://news.example/existing", "existing", "2026-07-15T09:00:00Z");
        NewsApiArticleDto invalid = article("", "invalid", "2026-07-15T12:00:00Z");
        when(articleRepository.findExistingUrls(anyList()))
                .thenReturn(Set.of("https://news.example/existing"));
        when(sourceService.preloadSources(List.of("Test Source")))
                .thenReturn(Map.of("Test Source", source));

        CollectionBatchStats stats = service.processArticlesWithStats(
                List.of(newArticle, repeated, existing, invalid), "us", "general");

        assertThat(stats.receivedCount()).isEqualTo(4);
        assertThat(stats.invalidCount()).isEqualTo(1);
        assertThat(stats.duplicateCount()).isEqualTo(2);
        assertThat(stats.savedCount()).isEqualTo(1);
        assertThat(stats.oldestPublishedAt()).isEqualTo(Instant.parse("2026-07-15T09:00:00Z"));
        assertThat(stats.latestPublishedAt()).isEqualTo(Instant.parse("2026-07-15T11:00:00Z"));
    }

    @Test
    void collectionEntryPoints_doNothingWhenNewsFetchIsDisabled() {
        ReflectionTestUtils.setField(service, "fetchEnabled", false);

        service.init();
        service.scheduledTopHeadlines();
        service.scheduledFetchFixed();

        verifyNoInteractions(restTemplate, articleRepository, sourceService, newsFetchConfig);
        verify(articleRepository, never()).saveAll(anyList());
    }

    private NewsApiArticleDto article(String url, String title) {
        return article(url, title, "2026-07-15T10:00:00Z");
    }

    private NewsApiArticleDto article(String url, String title, String publishedAt) {
        NewsApiSourceDto source = new NewsApiSourceDto();
        source.setName("Test Source");

        NewsApiArticleDto article = new NewsApiArticleDto();
        article.setAuthor("author");
        article.setTitle(title);
        article.setDescription("description");
        article.setContent("content");
        article.setUrl(url);
        article.setUrlToImage("https://news.example/image.jpg");
        article.setPublishedAt(publishedAt);
        article.setSource(source);
        return article;
    }

    private List<String> urlsOf(Iterable<Article> articles) {
        return StreamSupport.stream(articles.spliterator(), false)
                .map(Article::getUrl)
                .toList();
    }
}
