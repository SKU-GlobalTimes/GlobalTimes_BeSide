package com.example.globalTimes_be.domain.article.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.dto.ArticleResponseDto;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

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

       return articles.map(ArticleResponseDto::fromEntity);
    }

    // Hot News : 발행일 기준 이틀 내 기사만 출력되도록 수정 ( 랜딩 페이지네이션 토탈 : 42개 )
    public Page<ArticleResponseDto> getArticlesByViewCount(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        // 현재 시간에서 -2일 한 데이터들만 따로 출력 ( -1로 설정하면 출력되는 데이터 없음 )
        // Free : 하루 이전 기사들까지가 최신. 당일 기사 제공 X
        LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);

        Page<Article> articles = articleRepository.findByPublishedAtAfterOrderByViewCountDesc(twoDaysAgo, pageable);

        return articles.map(ArticleResponseDto::fromEntity);
    }
}
