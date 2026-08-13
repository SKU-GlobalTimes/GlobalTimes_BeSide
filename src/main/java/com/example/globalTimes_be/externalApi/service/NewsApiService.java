package com.example.globalTimes_be.externalApi.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.service.ArticleService;
import com.example.globalTimes_be.externalApi.config.NewsFetchConfig;
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
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;


// 전적인 API 호출 ( 외부 ) 처리 및 관리하는 서비스 레이어
@Slf4j
@Service
public class NewsApiService {

    private final RestTemplate restTemplate;
    private final ArticleRepository articleRepository;
    private final SourceService sourceService;
    private final NewsFetchConfig newsFetchConfig;

    @Value("${spring.newsapi.api-key}")
    private String apiKey;

    @Value("${spring.newsapi.base-url:https://newsapi.org}")
    private String newsApiBaseUrl;

    // 기존 통합 플래그를 기본값으로 사용하며 공급자별 플래그로 독립 제어한다.
    @Value("${news-fetch.news-api-enabled:${news-fetch.enabled:false}}")
    private boolean fetchEnabled;

    private int totalNewArticles = 0;
    private int requestCount = 0;

    @Autowired
    public NewsApiService(RestTemplate restTemplate, ArticleRepository articleRepository, SourceRepository sourceRepository, ArticleService articleService, SourceService sourceService, NewsFetchConfig newsFetchConfig) {
        this.restTemplate = restTemplate;
        this.articleRepository = articleRepository;
        this.sourceService = sourceService;
        this.newsFetchConfig = newsFetchConfig;
    }

    // 수집 테스트 시 news-fetch.enabled: true 로 변경 후 재실행
    @PostConstruct
    public void init() {
        if (!fetchEnabled) {
            log.info("[초기화] News API 수집 비활성화 → 초기 적재 건너뜀");
            return;
        }
        try {
            totalNewArticles = 0;
            requestCount = 0;
            fetchTopHeadlines(true);
            fetchDomainArticles(true);
        } catch (Exception e) {
            log.error("[초기화] 데이터 초기 적재 중 오류 발생", e);
        }
    }

    // 4시간마다 헤드라인 뉴스 Scheduling
    @Scheduled(cron = "0 0 0/4 * * *")
    public void scheduledTopHeadlines() {
        if (!fetchEnabled) return;
        try {
            totalNewArticles = 0;
            requestCount = 0;
            fetchTopHeadlines(false);
            log.info("[스케줄링] 헤드라인 저장된 신규 기사: {}개", totalNewArticles);
        } catch (Exception e) {
            log.error("[스케줄링] 헤드라인 뉴스 수집 중 오류 발생", e);
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledFetchFixed() {
        if (!fetchEnabled) return;
        try {
            totalNewArticles = 0;
            requestCount = 0;
            fetchDomainArticles(false);
            log.info("[스케줄링] Everything 저장된 신규 기사: {}개", totalNewArticles);
        } catch (Exception e) {
            log.error("[스케줄링] Everything 뉴스 수집 중 오류 발생", e);
        }
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

    // Country + Category 조합으로 헤드라인 수집 (국가/카테고리는 NewsFetchConfig에서 관리)
    void fetchTopHeadlines(boolean isInit) {
        outer:
        for (String country : newsFetchConfig.getCountries()) {
            for (String category : newsFetchConfig.getCategories()) {
                if (requestCount >= newsFetchConfig.getMaxRequestsPerRun()) {
                    log.warn("[요청 제한] 최대 요청 수({})에 도달해 헤드라인 수집을 중단합니다.", newsFetchConfig.getMaxRequestsPerRun());
                    break outer;
                }
                try {
                    String apiUrl = UriComponentsBuilder.fromUriString(newsApiBaseUrl)
                            .path("/v2/top-headlines")
                            .queryParam("country", country)
                            .queryParam("category", category)
                            .queryParam("pageSize", newsFetchConfig.getPageSize())
                            .queryParam("apiKey", apiKey)
                            .build()
                            .encode()
                            .toUriString();

                    requestCount++;
                    processApiRequest(apiUrl, country, category);
                    Thread.sleep(newsFetchConfig.getRequestDelayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[헤드라인] 딜레이 중 인터럽트 발생");
                    break outer;
                } catch (Exception e) {
                    log.error("[헤드라인] {} / {} 처리 중 오류 발생", country, category, e);
                }
            }
        }
        if (isInit) {
            log.info("[초기 적재] 헤드라인 저장된 신규 기사: {}개 (총 요청: {}회)", totalNewArticles, requestCount);
        }
    }

    private void fetchDomainArticles(boolean isInit) {
        for (String domain : newsFetchConfig.getDomains()) {
            if (requestCount >= newsFetchConfig.getMaxRequestsPerRun()) {
                log.warn("[요청 제한] 최대 요청 수({})에 도달해 도메인 수집을 중단합니다.", newsFetchConfig.getMaxRequestsPerRun());
                break;
            }
            try {
                String apiUrl = UriComponentsBuilder.fromUriString(newsApiBaseUrl)
                        .path("/v2/everything")
                        .queryParam("domains", domain)
                        .queryParam("pageSize", newsFetchConfig.getPageSize())
                        .queryParam("apiKey", apiKey)
                        .build()
                        .encode()
                        .toUriString();

                requestCount++;
                // 도메인 기사는 특정 국가에 종속되지 않으므로 "global"로 처리
                processApiRequest(apiUrl, "global", "general");
                Thread.sleep(newsFetchConfig.getRequestDelayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[Everything] 딜레이 중 인터럽트 발생");
                break;
            } catch (Exception e) {
                log.error("[Everything] {} 도메인 처리 중 오류 발생", domain, e);
            }
        }
        if (isInit) {
            log.info("[초기 적재] Everything 저장된 신규 기사: {}개 (총 요청: {}회)", totalNewArticles, requestCount);
        }
    }

    // 공통 API 호출 + 저장 처리
    private void processApiRequest(String apiUrl, String country, String category) {
        String label = country + "/" + category;
        try {
            ResponseEntity<NewsApiResponseDto> responseEntity = restTemplate.getForEntity(apiUrl, NewsApiResponseDto.class);
            NewsApiResponseDto response = responseEntity.getBody();

            if (response == null || response.getArticles() == null || response.getArticles().isEmpty()) {
                log.info("[수집 통계] {} | 응답 없음", label);
                return;
            }

            processArticles(response.getArticles(), country, category);

        } catch (Exception e) {
            log.error("[수집 오류] {} 처리 중 오류 발생", label, e);
        }
    }

    int processArticles(List<NewsApiArticleDto> articles, String country, String category) {
        return processArticlesWithStats(articles, country, category).savedCount();
    }

    CollectionBatchStats processArticlesWithStats(
            List<NewsApiArticleDto> articles, String country, String category) {
        Set<String> seenUrls = new HashSet<>();
        List<NewsApiArticleDto> uniqueValidArticles = new ArrayList<>();
        List<Instant> validPublishedAtValues = new ArrayList<>();
        int invalidCount = 0;
        int duplicateCount = 0;

        for (NewsApiArticleDto article : articles) {
            if (isInvalid(article)) {
                invalidCount++;
                continue;
            }
            validPublishedAtValues.add(OffsetDateTime.parse(article.getPublishedAt()).toInstant());
            if (!seenUrls.add(article.getUrl())) {
                duplicateCount++;
                continue;
            }
            uniqueValidArticles.add(article);
        }

        Set<String> existingUrls = uniqueValidArticles.isEmpty()
                ? Collections.emptySet()
                : articleRepository.findExistingUrls(new ArrayList<>(seenUrls));
        List<NewsApiArticleDto> newArticles = new ArrayList<>();
        for (NewsApiArticleDto article : uniqueValidArticles) {
            if (existingUrls.contains(article.getUrl())) {
                duplicateCount++;
            } else {
                newArticles.add(article);
            }
        }

        List<String> sourceNames = newArticles.stream()
                .map(NewsApiArticleDto::getSource)
                .map(NewsApiSourceDto::getName)
                .distinct()
                .collect(Collectors.toList());
        Map<String, Source> sourceCache = sourceNames.isEmpty()
                ? new HashMap<>()
                : sourceService.preloadSources(sourceNames);

        List<Article> articleList = newArticles.stream()
                .map(dto -> mapDtoToEntity(dto, country, category, sourceCache))
                .collect(Collectors.toList());
        if (!articleList.isEmpty()) {
            articleRepository.saveAll(articleList);
            totalNewArticles += articleList.size();
        }

        CollectionBatchStats stats = CollectionBatchStats.from(
                articles.size(), invalidCount, duplicateCount, articleList.size(), validPublishedAtValues);
        long sourceCount = uniqueValidArticles.stream()
                .map(NewsApiArticleDto::getSource)
                .map(NewsApiSourceDto::getName)
                .distinct()
                .count();
        log.info("[수집 통계] provider=news-api sourceCount={} country={} language=en category={} response={} invalid={} duplicate={} saved={} oldestPublishedAt={} latestPublishedAt={} freshnessSeconds={}",
                sourceCount, country, category, stats.receivedCount(), stats.invalidCount(),
                stats.duplicateCount(), stats.savedCount(), stats.oldestPublishedAt(), stats.latestPublishedAt(),
                stats.freshnessSeconds(Instant.now()));
        return stats;
    }

    private boolean isInvalid(NewsApiArticleDto dto) {
        if (dto.getAuthor() == null ||
                dto.getTitle() == null ||
                dto.getDescription() == null ||
                dto.getContent() == null ||
                dto.getUrl() == null ||
                dto.getUrl().isBlank() ||
                dto.getUrlToImage() == null ||
                dto.getPublishedAt() == null ||
                dto.getSource() == null ||
                dto.getSource().getName() == null ||
                dto.getSource().getName().isEmpty()) {
            return true;
        }
        try {
            OffsetDateTime.parse(dto.getPublishedAt());
        } catch (Exception e) {
            log.warn("[유효성 검사] publishedAt 형식 오류 - url: {}, publishedAt: {}",
                    dto.getUrl(), dto.getPublishedAt());
            return true;
        }
        return false;
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
