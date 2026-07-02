package com.example.globalTimes_be.domain.search.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.search.dto.response.SearchArticleDTO;
import com.example.globalTimes_be.domain.search.dto.response.SearchResDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Service
public class SearchArticlesService {
    private final ArticleRepository articleRepository;

    /**
     * @param date 탐색과 동일: 하루 단위(yyyy-MM-dd). null/공백이면 날짜 필터 없음.
     */
    @Transactional(readOnly = true)
    public SearchResDTO getSearchArticles(
            String text,
            String translatedText,
            String country,
            String category,
            String date
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

        String countryParam = (country != null && !country.isBlank()) ? country : null;
        String categoryParam = (category != null && !category.isBlank()) ? category : null;

        long searchStartedAt = System.nanoTime();
        List<Article> articles;
        if (isSameSearchText(text, translatedText)) {
            articles = articleRepository.searchByDescriptionOrTitleWithExploreFilters(
                    text,
                    countryParam,
                    categoryParam,
                    dateFrom,
                    dateTo
            );
        } else {
            articles = articleRepository.searchByDescriptionOrTitleWithExploreFilters(
                    text,
                    translatedText,
                    countryParam,
                    categoryParam,
                    dateFrom,
                    dateTo
            );
        }
        long searchMs = elapsedMs(searchStartedAt);
        
        // 검색결과에 대한 기사 DTO 리스트 생성
        List<SearchArticleDTO> searchArticleDTOs = new ArrayList<>();
        for (Article article : articles) {
            
            //만약 article이 비어있으면 패스
            if (article == null) {
                continue;
            }
            
            //기사 DTO 생성
            SearchArticleDTO searchArticleDTO = SearchArticleDTO.builder()
                    .id(article.getId())
                    .sourceName(article.getSource().getSourceName())
                    .title(article.getTitle())
                    .description(article.getDescription())
                    .urlToImage(article.getUrlToImage())
                    .publishedAt(article.getPublishedAt())
                    .build();
            
            //기사 DTO 리스트에 삽입
            searchArticleDTOs.add(searchArticleDTO);
        }
        
        log.info("[Search] textLength={} translatedLength={} country={} category={} date={} resultCount={} dbSearchMs={} totalMs={}",
                text.length(),
                translatedText.length(),
                countryParam,
                categoryParam,
                date,
                searchArticleDTOs.size(),
                searchMs,
                elapsedMs(startedAt));

        //결과 반환
        return SearchResDTO.builder()
                .originalText(text)
                .translatedText(translatedText)
                .searchArticles(searchArticleDTOs)
                .build();
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private boolean isSameSearchText(String text, String translatedText) {
        if (text == null || translatedText == null) {
            return false;
        }
        return text.trim().equalsIgnoreCase(translatedText.trim());
    }
}
