package com.example.globalTimes_be.domain.detail.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.detail.dto.response.DetailResDTO;
import com.example.globalTimes_be.domain.detail.dto.response.DetailResponseDTO;
import com.example.globalTimes_be.domain.detail.dto.response.RecentArticleDTO;
import com.example.globalTimes_be.domain.detail.exception.DetailErrorStatus;
import com.example.globalTimes_be.global.crawler.ArticleCrawler;
import com.example.globalTimes_be.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class DetailService {
    private final ArticleRepository articleRepository;
    private final ArticleCrawler articleCrawler;
    private final ArticleCrawlContentService articleCrawlContentService;

    @Transactional
    public DetailResDTO getNewsDetail(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new BaseException(DetailErrorStatus._EMPTY_NEWS_DATA.getResponse()));

        article.increaseViewCount();
        articleRepository.save(article);

        DetailResponseDTO detailResponseDTO = DetailResponseDTO.builder()
                .sourceName(article.getSource().getSourceName())
                .author(article.getAuthor())
                .title(article.getTitle())
                .viewCount(article.getViewCount())
                .url(article.getUrl())
                .urlToImage(article.getUrlToImage())
                .publishedAt(article.getPublishedAt())
                .build();

        List<Article> articleList = articleRepository.findTop20ByIdNotOrderByPublishedAtDesc(id);

        List<RecentArticleDTO> recentArticleDTOList = articleList.stream()
                .map(news -> RecentArticleDTO.builder()
                        .id(news.getId())
                        .sourceName(news.getSource().getSourceName())
                        .title(news.getTitle())
                        .urlToImage(news.getUrlToImage())
                        .publishedAt(news.getPublishedAt())
                        .build())
                .toList();

        return DetailResDTO.builder()
                .newsDetail(detailResponseDTO)
                .recentNewsList(recentArticleDTOList)
                .build();
    }

    @Transactional(readOnly = true)
    public String getArticleContent(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new BaseException(DetailErrorStatus._EMPTY_NEWS_DATA.getResponse()));

        return article.getContent();
    }

    // DB에 저장된 요약 조회 (없으면 null 반환)
    @Transactional(readOnly = true)
    public String getArticleSummary(Long id, String language) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new BaseException(DetailErrorStatus._EMPTY_NEWS_DATA.getResponse()));

        return article.getSummary();
    }

    // GPT 요약 결과를 DB에 저장
    @Transactional
    public void saveArticleSummary(Long id, String summary) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new BaseException(DetailErrorStatus._EMPTY_NEWS_DATA.getResponse()));

        article.updateSummary(summary);
        articleRepository.save(article);
    }

    // 크롤링(네트워크 I/O)은 트랜잭션 밖에서 실행하고, DB 조회/저장은 별도 트랜잭션에서 처리한다.
    public String getArticleCrawledContent(Long id) {
        ArticleCrawlContentService.ArticleCrawlTarget target = articleCrawlContentService.getCrawlTarget(id);
        if (target.crawledContent() != null && !target.crawledContent().isBlank()) {
            return target.crawledContent();
        }

        String crawledContent = articleCrawler.crawlParagraphs(target.url())
                .orElse(null);
        if (crawledContent == null) {
            return null;
        }

        articleCrawlContentService.saveCrawledContent(id, crawledContent);
        return crawledContent;
    }
}
