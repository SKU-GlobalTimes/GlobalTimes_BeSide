package com.example.globalTimes_be.domain.article.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.dto.ArticleResponseDto;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;

    public ArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    // 최신순 기사
    public Page<ArticleResponseDto> getArticlesByPublishedAt(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Article> articles = articleRepository.findAllByOrderByPublishedAtDesc(pageable);

        // Dto 변환
        return articles.map(article -> new ArticleResponseDto(
                article.getId(),
                article.getSource().getSourceName(),
                article.getPublishedAt(),
                article.getTitle(),
                article.getDescription(),
                article.getUrlToImage(),
                article.getViewCount()
        ));
    }

    // 조회수 기준
    public Page<ArticleResponseDto> getArticlesByViewCount(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Article> articles = articleRepository.findAllByOrderByViewCountDesc(pageable);

        // Dto 변환
        return articles.map(article -> new ArticleResponseDto(
                article.getId(),
                article.getSource().getSourceName(),  // sourceName이 null이 아니므로 바로 사용
                article.getPublishedAt(),
                article.getTitle(),
                article.getDescription(),
                article.getUrlToImage(),
                article.getViewCount()
        ));
    }
}
