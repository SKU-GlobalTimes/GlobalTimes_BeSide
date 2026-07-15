package com.example.globalTimes_be.domain.detail.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.detail.dto.response.DetailResDTO;
import com.example.globalTimes_be.domain.source.entity.Source;
import com.example.globalTimes_be.global.crawler.ArticleCrawler;
import com.example.globalTimes_be.global.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
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
    void getNewsDetail_incrementsViewCountBeforeReadingUpdatedArticle() {
        Article article = articleWithViewCountOne();
        when(articleRepository.incrementViewCount(1L)).thenReturn(1);
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(articleRepository.findTop20ByIdNotOrderByPublishedAtDesc(1L)).thenReturn(List.of());

        DetailResDTO result = detailService.getNewsDetail(1L);

        assertThat(result.getNewsDetail().getViewCount()).isEqualTo(1L);
        InOrder inOrder = inOrder(articleRepository);
        inOrder.verify(articleRepository).incrementViewCount(1L);
        inOrder.verify(articleRepository).findById(1L);
        verify(articleRepository, never()).save(article);
    }

    @Test
    void getNewsDetail_throwsWhenAtomicUpdateFindsNoArticle() {
        when(articleRepository.incrementViewCount(99L)).thenReturn(0);

        assertThatThrownBy(() -> detailService.getNewsDetail(99L))
                .isInstanceOf(BaseException.class);

        verify(articleRepository, never()).findById(99L);
    }

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

    private Article articleWithViewCountOne() {
        Article article = Article.createArticle(
                Source.createSource("Test Source", null),
                "author",
                "title",
                "description",
                "content",
                "https://news.example/1",
                "https://news.example/image.jpg",
                "2026-07-15T10:00:00Z",
                "us",
                "general"
        );
        ReflectionTestUtils.setField(article, "viewCount", 1L);
        return article;
    }
}
