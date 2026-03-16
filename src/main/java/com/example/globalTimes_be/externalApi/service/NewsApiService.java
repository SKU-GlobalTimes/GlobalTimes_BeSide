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
import org.springframework.web.client.RestTemplate;

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
    private int totalNewArticles = 0;
    private int requestCount = 0;

    @Autowired
    public NewsApiService(RestTemplate restTemplate, ArticleRepository articleRepository, SourceRepository sourceRepository, ArticleService articleService, SourceService sourceService, NewsFetchConfig newsFetchConfig) {
        this.restTemplate = restTemplate;
        this.articleRepository = articleRepository;
        this.sourceService = sourceService;
        this.newsFetchConfig = newsFetchConfig;
    }

    // @PostConstruct 비활성화 (개발 중 API 할당량 절약 목적, 수집 테스트 시 활성화)
    // @PostConstruct
    public void init() {
        try {
            resetCounters();
            fetchTopHeadlines(true);
            fetchDomainArticles(true);
        } catch (Exception e) {
            log.error("[초기화] 데이터 초기 적재 중 오류 발생", e);
        }
    }

    // 4시간마다 헤드라인 뉴스 Scheduling
    @Scheduled(cron = "0 0 0/4 * * *")
    public void scheduledTopHeadlines() {
        try {
            resetCounters();
            fetchTopHeadlines(false);
            log.info("[스케줄링] 헤드라인 저장된 신규 기사: {}개", totalNewArticles);
        } catch (Exception e) {
            log.error("[스케줄링] 헤드라인 뉴스 수집 중 오류 발생", e);
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledFetchFixed() {
        try {
            resetCounters();
            fetchDomainArticles(false);
            log.info("[스케줄링] Everything 저장된 신규 기사: {}개", totalNewArticles);
        } catch (Exception e) {
            log.error("[스케줄링] Everything 뉴스 수집 중 오류 발생", e);
        }
    }

    private void resetCounters() {
        totalNewArticles = 0;
        requestCount = 0;
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
    private void fetchTopHeadlines(boolean isInit) {
        outer:
        for (String country : newsFetchConfig.getCountries()) {
            for (String category : newsFetchConfig.getCategories()) {
                if (requestCount >= newsFetchConfig.getMaxRequestsPerRun()) {
                    log.warn("[요청 제한] 최대 요청 수({})에 도달해 헤드라인 수집을 중단합니다.",
                            newsFetchConfig.getMaxRequestsPerRun());
                    break outer;
                }
                try {
                    String apiUrl = "https://newsapi.org/v2/top-headlines?"
                            + "country=" + country
                            + "&category=" + category
                            + "&pageSize=" + newsFetchConfig.getPageSize()
                            + "&apiKey=" + apiKey;

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
                log.warn("[요청 제한] 최대 요청 수({})에 도달해 도메인 수집을 중단합니다.",
                        newsFetchConfig.getMaxRequestsPerRun());
                break;
            }
            try {
                String apiUrl = "https://newsapi.org/v2/everything?"
                        + "domains=" + domain
                        + "&pageSize=" + newsFetchConfig.getPageSize()
                        + "&apiKey=" + apiKey;

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
                log.info("[수집 통계] {} | 응답 없음(0건)", label);
                return;
            }

            List<NewsApiArticleDto> articles = response.getArticles();
            int total = articles.size();

            // 기존 URL 조회
            List<String> urls = articles.stream()
                    .map(NewsApiArticleDto::getUrl)
                    .collect(Collectors.toList());
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

            // 단계별 카운트 계산
            long invalidCount = articles.stream()
                    .filter(this::isInvalid)
                    .count();
            long duplicateCount = articles.stream()
                    .filter(dto -> !isInvalid(dto) && existingUrls.contains(dto.getUrl()))
                    .count();

            // 유효성 검증 + 저장
            List<Article> articleList = articles.stream()
                    .filter(dto -> !isInvalid(dto) && !existingUrls.contains(dto.getUrl()))
                    .map(dto -> mapDtoToEntity(dto, country, category, sourceCache))
                    .collect(Collectors.toList());

            if (!articleList.isEmpty()) {
                articleRepository.saveAll(articleList);
                totalNewArticles += articleList.size();
            }

            log.info("[수집 통계] {} | 응답:{}, invalid:{}, 중복:{}, 저장:{}",
                    label, total, invalidCount, duplicateCount, articleList.size());

        } catch (Exception e) {
            log.error("[수집 오류] {} 처리 중 오류 발생", label, e);
        }
    }

    private boolean isInvalid(NewsApiArticleDto dto){
        // 하나라도 null 인 케이스들을 방지
        if (dto.getAuthor() == null ||
                dto.getTitle() == null ||
                dto.getDescription() == null ||
                dto.getContent() == null ||
                dto.getUrl() == null ||
                dto.getUrlToImage() == null ||
                dto.getPublishedAt() == null ||
                dto.getSource() == null ||
                dto.getSource().getName() == null ||
                dto.getSource().getName().isEmpty()) {
            return true;
        }
        // publishedAt ISO 8601 형식 검증 (파싱 실패 시 invalid 처리)
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
