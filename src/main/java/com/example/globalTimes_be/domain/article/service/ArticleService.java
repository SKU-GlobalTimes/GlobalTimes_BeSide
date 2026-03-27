package com.example.globalTimes_be.domain.article.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.dto.ArticleResponseDto;
import com.example.globalTimes_be.domain.article.dto.CursorArticleResponseDto;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

    // Cursor 기반 최신순 페이징
    // size+1개를 조회해 hasNext 판별, published_at 인덱스 활용으로 Offset 대비 일정한 탐색 속도
    @Transactional(readOnly = true)
    public CursorArticleResponseDto getArticlesByCursor(String cursor, int size) {
        Pageable pageable = PageRequest.of(0, size + 1);

        List<Article> articles = (cursor == null)
                ? articleRepository.findFirstPage(pageable)
                : articleRepository.findByCursor(
                        LocalDateTime.parse(cursor, DateTimeFormatter.ISO_LOCAL_DATE_TIME), pageable);

        boolean hasNext = articles.size() > size;
        List<Article> result = hasNext ? articles.subList(0, size) : articles;

        String nextCursor = hasNext
                ? result.get(result.size() - 1).getPublishedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null;

        List<ArticleResponseDto> dtos = result.stream()
                .map(ArticleResponseDto::fromEntity)
                .toList();

        return new CursorArticleResponseDto(dtos, nextCursor, hasNext);
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
