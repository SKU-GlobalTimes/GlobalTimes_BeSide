package com.example.globalTimes_be.externalApi.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.service.ArticleService;
import com.example.globalTimes_be.domain.externalApi.dto.NewsApiArticleDto;
import com.example.globalTimes_be.domain.externalApi.dto.NewsApiResponseDto;
import com.example.globalTimes_be.domain.externalApi.dto.NewsApiSourceDto;
import com.example.globalTimes_be.domain.source.entity.Source;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.source.repository.SourceRepository;
import com.example.globalTimes_be.domain.source.service.SourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

// 전적인 API 호출 ( 외부 ) 처리 및 관리하는 서비스 레이어
@Slf4j
@Service
public class NewsApiService {

    private final RestTemplate restTemplate;
    private final ArticleRepository articleRepository;
    private final SourceService sourceService;

    private static final List<String> COUNTRIES = List.of("us");

    private static final List<String> CATEGORY_LIST = Arrays.asList(
            null, // general
            "business",
            "entertainment",
            "health",
            "science",
            "sports",
            "technology"
    );

    @Autowired
    public NewsApiService(RestTemplate restTemplate, ArticleRepository articleRepository, SourceRepository sourceRepository, ArticleService articleService, SourceService sourceService) {
        this.restTemplate = restTemplate;
        this.articleRepository = articleRepository;
        this.sourceService = sourceService;
    }

    @Scheduled(fixedRate = 300000, initialDelay = 10000) // 5분마다 실행 ( 초기 10 초 지연 )
    public void fetchAndSaveNews() {
        int pageSize = 100; // 최대 100개 요청

        int totalNewArticles = 0;

        for (String country : COUNTRIES) {
            for (String category : CATEGORY_LIST) {
                String categoryParam = (category == null) ? "" : "&category=" + category;
                String apiUrl = "https://newsapi.org/v2/top-headlines?"
                        + "country=" + country
                        + categoryParam
                        + "&pageSize=" + pageSize
                        + "&apiKey=" + "{API_KEY}";

                ResponseEntity<NewsApiResponseDto> responseEntity = restTemplate.getForEntity(apiUrl, NewsApiResponseDto.class);
                NewsApiResponseDto response = responseEntity.getBody();

                if (response == null || response.getArticles() == null || response.getArticles().isEmpty()) {
                    System.out.println("가져올 뉴스 없음: " + country + " / " + (category == null ? "general" : category));
                    continue;
                }

                List<NewsApiArticleDto> articles = response.getArticles();
                List<String> urls = articles.stream().map(NewsApiArticleDto::getUrl).collect(Collectors.toList());

                // 기존 저장된 URL 조회 (중복 방지)
                Set<String> existingUrls = articleRepository.findExistingUrls(urls);

                // Source 캐싱
                List<String> sourceNames = articles.stream()
                        .map(NewsApiArticleDto::getSource)
                        .filter(Objects::nonNull)
                        .map(NewsApiSourceDto::getName)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());

                Map<String, Source> sourceCache = sourceService.preloadSources(sourceNames);

                String safeCategory = (category == null) ? "general" : category;

                List<Article> articleList = articles.stream()
                        .filter(articleDto -> !isInvalid(articleDto) && !existingUrls.contains(articleDto.getUrl()))
                        .map(articleDto -> mapDtoToEntity(articleDto, country, safeCategory, sourceCache)) // 캐시 사용
                        .collect(Collectors.toList());

                if (!articleList.isEmpty()) {
                    articleRepository.saveAll(articleList);
                    System.out.println(country + " / " + (category == null ? "general" : category) + " - " + articleList.size() + "개 저장 완료");
                    totalNewArticles += articleList.size();
                }
            }
        }
        System.out.println("총 저장된 신규 기사: " + totalNewArticles + "개");
    }

    private boolean isInvalid(NewsApiArticleDto dto){
        // 하나라도 null 인 케이스들을 방지
        return dto.getAuthor() == null ||
                dto.getTitle() == null ||
                dto.getDescription() == null ||
                dto.getContent() == null ||
                dto.getUrl() == null ||
                dto.getUrlToImage() == null ||
                dto.getSource() == null ||
                dto.getSource().getName() == null ||
                dto.getSource().getName().isEmpty();
    }

    private Article mapDtoToEntity(NewsApiArticleDto articleDto, String countryCode,
                                   String category,  Map<String, Source> sourceCache) {
        // Source가 null이 아닐 경우에만 변환
        String sourceName = Optional.ofNullable(articleDto.getSource())
                .map(NewsApiSourceDto::getName)
                .orElse("Unknown");

        Source source = sourceCache.get(sourceName);

        if (source == null) {
            source = sourceService.getOrCreateSource(sourceName, null); // 영속 상태의 Source
            sourceCache.put(sourceName, source);
        }
        return Article.createArticle(
                source,
                articleDto.getAuthor(),
                articleDto.getTitle(),
                articleDto.getDescription(),
                articleDto.getContent(),
                articleDto.getUrl(),
                articleDto.getUrlToImage(),
                articleDto.getPublishedAt(),
                countryCode,
                (category == null ? "general" : category)
        );
    }
}
