package com.example.globalTimes_be.domain.detail.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.detail.exception.DetailErrorStatus;
import com.example.globalTimes_be.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ArticleCrawlContentService {

    private final ArticleRepository articleRepository;

    @Transactional(readOnly = true)
    public ArticleCrawlTarget getCrawlTarget(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new BaseException(DetailErrorStatus._EMPTY_NEWS_DATA.getResponse()));

        return new ArticleCrawlTarget(article.getUrl(), article.getCrawledContent());
    }

    @Transactional
    public void saveCrawledContent(Long id, String crawledContent) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new BaseException(DetailErrorStatus._EMPTY_NEWS_DATA.getResponse()));

        article.updateCrawledContent(crawledContent);
        articleRepository.save(article);
    }

    public record ArticleCrawlTarget(String url, String crawledContent) {
    }
}
