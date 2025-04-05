package com.example.globalTimes_be.externalApi.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.service.ArticleService;
import com.example.globalTimes_be.externalApi.dto.NewsApiArticleDto;
import com.example.globalTimes_be.externalApi.dto.NewsApiResponseDto;
import com.example.globalTimes_be.externalApi.dto.NewsApiSourceDto;
import com.example.globalTimes_be.domain.source.entity.Source;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.source.repository.SourceRepository;
import com.example.globalTimes_be.domain.source.service.SourceService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private static final List<String> DOMAINS = Arrays.asList("wsj.com", "bbc.co.uk", "techcrunch.com");

    @Value("${spring.newsapi.api-key}")
    private String apiKey;
    private int totalNewArticles = 0;

    @Autowired
    public NewsApiService(RestTemplate restTemplate, ArticleRepository articleRepository, SourceRepository sourceRepository, ArticleService articleService, SourceService sourceService) {
        this.restTemplate = restTemplate;
        this.articleRepository = articleRepository;
        this.sourceService = sourceService;
    }

    @PostConstruct
    public void init() {
        fetchTopHeadlines(true);    // 뉴스 초기 적재
        fetchDomainArticles(true);
    }

    // 3시간마다 헤드라인 뉴스 Scheduling
    @Scheduled(cron = "0 0 0/3 * * *")
    public void scheduledTopHeadlines() {
        totalNewArticles = 0;
        fetchTopHeadlines(false);
        log.info("[스케줄링] 헤드라인 저장된 신규 기사: {}개", totalNewArticles);
    }


    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledFetchFixed() {
        totalNewArticles = 0;
        fetchDomainArticles(false);
        log.info("[스케줄링] Everything 저장된 신규 기사: {}개", totalNewArticles);
    }

    // 최상위 실행 메소드 ( 두 방식 동시에 테스트용 -> Scheduling 제외 )
    /*
    public void fetchAndSave() {
        totalNewArticles = 0; // 매번 초기화
        fetchTopHeadlines();
        fetchDomainArticles();
        System.out.println("총 저장된 신규 기사: " + totalNewArticles + "개");
    }
    */

    // Country + Category ( 기존의 헤드라인 호출 ) : 헤드라인이기에 좀 더 자주 스케줄링하고
    private void fetchTopHeadlines(boolean isInit) {
        for (String country : COUNTRIES) {
            for (String category : CATEGORY_LIST) {
                String categoryParam = (category == null) ? "" : "&category=" + category;
                String apiUrl = "https://newsapi.org/v2/top-headlines?"
                        + "country=" + country
                        + categoryParam
                        + "&pageSize=" + 100
                        + "&apiKey=" + apiKey;

                processApiRequest(apiUrl, country, (category == null ? "general" : category));
            }
        }
        if (isInit) {
            log.info("[초기 적재] 헤드라인 저장된 신규 기사: {}개", totalNewArticles);
        }
    }

    private void fetchDomainArticles(boolean isInit) {
        for (String domain : DOMAINS) {
            String apiUrl = "https://newsapi.org/v2/everything?"
                    + "domains=" + domain
                    + "&pageSize=" + 100
                    + "&apiKey=" + apiKey;

            processApiRequest(apiUrl, "us", null);
        }
        if (isInit) {
            log.info("[초기 적재] Everything 저장된 신규 기사: {}개", totalNewArticles);
        }
    }

    // 공통 API 호출 + 저장 처리
    private void processApiRequest(String apiUrl, String country, String category) {
        ResponseEntity<NewsApiResponseDto> responseEntity = restTemplate.getForEntity(apiUrl, NewsApiResponseDto.class);
        NewsApiResponseDto response = responseEntity.getBody();

        if (response == null || response.getArticles() == null || response.getArticles().isEmpty()) {
            log.info("가져올 뉴스 없음: {} / {}", (country != null ? country : "도메인"), category);
            return;
        }

        List<NewsApiArticleDto> articles = response.getArticles();
        List<String> urls = articles.stream()
                .map(NewsApiArticleDto::getUrl)
                .collect(Collectors.toList());

        // 기존 URL 조회
        Set<String> existingUrls = articleRepository.findExistingUrls(urls);

        // Source 캐시
        List<String> sourceNames = articles.stream()
                .map(NewsApiArticleDto::getSource)
                .filter(Objects::nonNull)
                .map(NewsApiSourceDto::getName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Source> sourceCache = sourceService.preloadSources(sourceNames);

        // 유효성 검증 + 저장
        List<Article> articleList = articles.stream()
                .filter(articleDto -> !isInvalid(articleDto) && !existingUrls.contains(articleDto.getUrl()))
                .map(articleDto -> mapDtoToEntity(articleDto, country, category, sourceCache))
                .collect(Collectors.toList());

        if (!articleList.isEmpty()) {
            articleRepository.saveAll(articleList);
            log.info("{} / {} - {}개 저장 완료", (country != null ? country : "도메인"), category, articleList.size());
            totalNewArticles += articleList.size();
        }
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
