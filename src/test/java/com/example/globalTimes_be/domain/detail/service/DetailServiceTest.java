package com.example.globalTimes_be.domain.detail.service;

import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.global.crawler.ArticleCrawler;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DetailServiceTest {

    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final ArticleCrawler articleCrawler = mock(ArticleCrawler.class);
    private final ArticleCrawlContentService articleCrawlContentService = mock(ArticleCrawlContentService.class);
    private final DetailService detailService = new DetailService(
            articleRepository,
            articleCrawler,
            articleCrawlContentService
    );

    @Test
    void getArticleCrawledContent_returnsCachedContentWithoutCrawling() {
        when(articleCrawlContentService.getCrawlTarget(1L))
                .thenReturn(new ArticleCrawlContentService.ArticleCrawlTarget("https://news.example/1", "cached"));

        String result = detailService.getArticleCrawledContent(1L);

        assertThat(result).isEqualTo("cached");
        verify(articleCrawler, never()).crawlParagraphs("https://news.example/1");
        verify(articleCrawlContentService, never()).saveCrawledContent(1L, "cached");
    }

    @Test
    void getArticleCrawledContent_returnsNullAndDoesNotSaveWhenCrawlingFails() {
        when(articleCrawlContentService.getCrawlTarget(1L))
                .thenReturn(new ArticleCrawlContentService.ArticleCrawlTarget("https://news.example/1", null));
        when(articleCrawler.crawlParagraphs("https://news.example/1"))
                .thenReturn(Optional.empty());

        String result = detailService.getArticleCrawledContent(1L);

        assertThat(result).isNull();
        verify(articleCrawlContentService, never()).saveCrawledContent(1L, "crawled");
    }

    @Test
    void getArticleCrawledContent_savesAndReturnsCrawledContent() {
        when(articleCrawlContentService.getCrawlTarget(1L))
                .thenReturn(new ArticleCrawlContentService.ArticleCrawlTarget("https://news.example/1", null));
        when(articleCrawler.crawlParagraphs("https://news.example/1"))
                .thenReturn(Optional.of("crawled"));

        String result = detailService.getArticleCrawledContent(1L);

        assertThat(result).isEqualTo("crawled");
        verify(articleCrawlContentService).saveCrawledContent(1L, "crawled");
    }
}
