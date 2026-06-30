package com.example.globalTimes_be.domain.article.service;

import com.example.globalTimes_be.domain.article.dto.ArticleResponseDto;
import com.example.globalTimes_be.domain.article.dto.CursorArticleResponseDto;
import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Service
public class ExploreService {

    private final ArticleRepository articleRepository;

    @Transactional(readOnly = true)
    public CursorArticleResponseDto explore(
            String country,
            String category,
            String date,
            String cursorStr,
            int size
    ) {
        long startedAt = System.nanoTime();
        LocalDateTime dateFrom = null;
        LocalDateTime dateTo = null;
        if (date != null && !date.isBlank()) {
            try {
                LocalDate localDate = LocalDate.parse(date);
                dateFrom = localDate.atStartOfDay();
                dateTo = localDate.atTime(LocalTime.MAX);
            } catch (DateTimeParseException ignored) {
            }
        }

        LocalDateTime cursor = null;
        if (cursorStr != null && !cursorStr.isBlank()) {
            try {
                cursor = LocalDateTime.parse(cursorStr);
            } catch (DateTimeParseException ignored) {
            }
        }

        String countryParam = (country != null && !country.isBlank()) ? country : null;
        String categoryParam = (category != null && !category.isBlank()) ? category : null;

        long queryStartedAt = System.nanoTime();
        List<Article> fetched = articleRepository.findByExploreFilters(
                countryParam, categoryParam, dateFrom, dateTo, cursor,
                PageRequest.of(0, size + 1)
        );
        long queryMs = elapsedMs(queryStartedAt);

        boolean hasNext = fetched.size() > size;
        List<Article> articles = hasNext ? fetched.subList(0, size) : fetched;

        String nextCursor = null;
        if (hasNext && !articles.isEmpty()) {
            nextCursor = articles.get(articles.size() - 1).getPublishedAt().toString();
        }

        List<ArticleResponseDto> dtos = articles.stream()
                .map(ArticleResponseDto::fromEntity)
                .toList();

        log.info("[Explore] country={} category={} date={} cursorProvided={} size={} resultCount={} hasNext={} dbQueryMs={} totalMs={}",
                countryParam,
                categoryParam,
                date,
                cursor != null,
                size,
                dtos.size(),
                hasNext,
                queryMs,
                elapsedMs(startedAt));

        return new CursorArticleResponseDto(dtos, nextCursor, hasNext);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
