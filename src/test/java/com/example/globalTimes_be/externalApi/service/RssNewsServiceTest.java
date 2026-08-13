package com.example.globalTimes_be.externalApi.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.source.entity.Source;
import com.example.globalTimes_be.domain.source.service.SourceService;
import com.example.globalTimes_be.externalApi.config.RssFeedConfig;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RssNewsServiceTest {

    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final SourceService sourceService = mock(SourceService.class);
    private final RssFeedConfig rssFeedConfig = mock(RssFeedConfig.class);
    private final RssNewsService service = new RssNewsService(articleRepository, sourceService, rssFeedConfig);
    private final RssFeedConfig.FeedSource feed = new RssFeedConfig.FeedSource(
            "https://feed.example/rss", "gb", "en", "Test RSS", "general"
    );

    @Test
    void processItems_savesOneArticleForDuplicateUrlsAndSkipsExistingUrl() {
        Source source = Source.createSource("Test RSS", null);
        Elements items = items(
                item("https://news.example/new", "new"),
                item("https://news.example/new", "repeated"),
                item("https://news.example/existing", "existing")
        );
        when(articleRepository.findExistingUrls(anyList()))
                .thenReturn(Set.of("https://news.example/existing"));
        when(sourceService.getOrCreateSource("Test RSS", null)).thenReturn(source);

        int saved = service.processItems(feed, items);

        assertThat(saved).isEqualTo(1);
        verify(articleRepository).saveAll(argThat(articles -> urlsOf(articles)
                .equals(List.of("https://news.example/new"))));
    }

    @Test
    void processItems_doesNotSaveWhenSameFeedIsProcessedAgain() {
        Source source = Source.createSource("Test RSS", null);
        Elements items = items(item("https://news.example/retry", "retry"));
        when(articleRepository.findExistingUrls(anyList()))
                .thenReturn(Set.of(), Set.of("https://news.example/retry"));
        when(sourceService.getOrCreateSource("Test RSS", null)).thenReturn(source);

        int firstSaved = service.processItems(feed, items);
        int secondSaved = service.processItems(feed, items);

        assertThat(firstSaved).isEqualTo(1);
        assertThat(secondSaved).isZero();
        verify(articleRepository).saveAll(argThat(articles -> urlsOf(articles)
                .equals(List.of("https://news.example/retry"))));
    }

    @Test
    void processItemsWithStats_reportsCountsAndPublicationRange() {
        Source source = Source.createSource("Test RSS", null);
        Elements items = items(
                item("https://news.example/new", "new", "Wed, 15 Jul 2026 10:00:00 GMT"),
                item("https://news.example/new", "repeated", "Wed, 15 Jul 2026 11:00:00 GMT"),
                item("https://news.example/existing", "existing", "Wed, 15 Jul 2026 09:00:00 GMT"),
                item("https://news.example/invalid", "invalid", "not-a-date")
        );
        when(articleRepository.findExistingUrls(anyList()))
                .thenReturn(Set.of("https://news.example/existing"));
        when(sourceService.getOrCreateSource("Test RSS", null)).thenReturn(source);

        CollectionBatchStats stats = service.processItemsWithStats(feed, items);

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
        service.scheduledFetch();

        verifyNoInteractions(articleRepository, sourceService, rssFeedConfig);
    }

    @Test
    void selectedFeeds_filtersCountriesAndAppliesRunLimit() {
        RssFeedConfig.FeedSource englishFeed = new RssFeedConfig.FeedSource(
                "https://feed.example/en", "gb", "en", "English RSS", "general");
        RssFeedConfig.FeedSource koreanFeed = new RssFeedConfig.FeedSource(
                "https://feed.example/ko", "kr", "ko", "Korean RSS", "general");
        RssFeedConfig.FeedSource secondKoreanFeed = new RssFeedConfig.FeedSource(
                "https://feed.example/ko-2", "kr", "ko", "Korean RSS", "business");
        when(rssFeedConfig.getFeeds()).thenReturn(List.of(englishFeed, koreanFeed, secondKoreanFeed));
        ReflectionTestUtils.setField(service, "fetchCountries", " KR ");
        ReflectionTestUtils.setField(service, "maxFeedsPerRun", 1);

        assertThat(service.selectedFeeds()).containsExactly(koreanFeed);
    }

    @Test
    void limitArticles_capsCandidatesForExternalSmokeRun() {
        Elements items = items(
                item("https://news.example/1", "one"),
                item("https://news.example/2", "two"),
                item("https://news.example/3", "three")
        );
        ReflectionTestUtils.setField(service, "maxArticlesPerFeed", 2);

        Elements limited = service.limitArticles(items);

        assertThat(limited).hasSize(2);
        assertThat(limited.eachText()).allMatch(text -> !text.contains("three"));
    }

    private Elements items(String... items) {
        return Jsoup.parse("<rss><channel>" + String.join("", items) + "</channel></rss>", "", Parser.xmlParser())
                .select("item");
    }

    private String item(String url, String title) {
        return item(url, title, "Wed, 15 Jul 2026 10:00:00 GMT");
    }

    private String item(String url, String title, String publishedAt) {
        return "<item><link>" + url + "</link><title>" + title + "</title>"
                + "<pubDate>" + publishedAt + "</pubDate>"
                + "<description>description</description></item>";
    }

    private List<String> urlsOf(Iterable<Article> articles) {
        return StreamSupport.stream(articles.spliterator(), false)
                .map(Article::getUrl)
                .toList();
    }
}
