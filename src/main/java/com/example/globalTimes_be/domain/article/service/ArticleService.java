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
    @Transactional(readOnly = true)
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

    // Hot News : 조회수 높은 순 (최근 30일 이내 기사 대상)
    @Transactional(readOnly = true)
    public Page<ArticleResponseDto> getArticlesByViewCount(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        // 최근 30일 이내 기사 중 조회수 높은 순으로 정렬
        // (기존 2일 필터는 실시간 수집 환경에서만 유효 → 로컬/테스트 환경에서 데이터 없음 이슈 방지)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        Page<Article> articles = articleRepository.findByPublishedAtAfterOrderByViewCountDesc(thirtyDaysAgo, pageable);

        return articles.map(ArticleResponseDto::fromEntity);
    }
}
