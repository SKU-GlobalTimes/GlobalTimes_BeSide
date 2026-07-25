package com.example.globalTimes_be.externalApi.service;

import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.article.service.ArticleService;
import com.example.globalTimes_be.domain.source.repository.SourceRepository;
import com.example.globalTimes_be.domain.source.service.SourceService;
import com.example.globalTimes_be.externalApi.config.NewsFetchConfig;
import com.example.globalTimes_be.externalApi.config.RssFeedConfig;
import com.example.globalTimes_be.externalApi.dto.NewsApiArticleDto;
import com.example.globalTimes_be.externalApi.dto.NewsApiResponseDto;
import com.example.globalTimes_be.externalApi.dto.NewsApiSourceDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(SourceService.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
class CollectionBatchPerformanceIntegrationTest {

    private static final int[] BATCH_SIZES = {100, 500, 1_000};
    private static final int MEASUREMENT_RUNS = 3;
    private static final RssFeedConfig.FeedSource RSS_FEED = new RssFeedConfig.FeedSource(
            "https://fixture.example/rss", "gb", "en", "Fixture RSS", "general"
    );

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withUsername("root")
            .withPassword("test");

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private SourceService sourceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        clearTables();
    }

    @Test
    void newsApiBatchMeasuresNewSaveAndDuplicateRerunAtIncreasingSizes() {
        warmUpNewsApi();

        for (int size : BATCH_SIZES) {
            List<BatchMeasurement> measurements = new ArrayList<>();
            for (int run = 1; run <= MEASUREMENT_RUNS; run++) {
                measurements.add(measureNewsBatch(size, run));
            }
            printMedian("news-api", size, measurements);
        }
    }

    @Test
    void rssBatchMeasuresNewSaveAndDuplicateRerunAtIncreasingSizes() {
        warmUpRss();

        for (int size : BATCH_SIZES) {
            List<BatchMeasurement> measurements = new ArrayList<>();
            for (int run = 1; run <= MEASUREMENT_RUNS; run++) {
                measurements.add(measureRssBatch(size, run));
            }
            printMedian("rss", size, measurements);
        }
    }

    @Test
    void mixedNewsApiBatchClassifiesInvalidAndDuplicateArticles() {
        clearTables();
        NewsApiService service = newsApiService(mock(NewsFetchConfig.class));
        service.processArticlesWithStats(validNewsArticles(100, "existing"), "us", "general");

        List<NewsApiArticleDto> mixed = mixedNewsArticles();
        statistics.clear();
        long startedAt = System.nanoTime();

        CollectionBatchStats stats = service.processArticlesWithStats(mixed, "us", "general");
        double elapsedMs = elapsedMs(startedAt);
        long statements = statistics.getPrepareStatementCount();

        assertThat(stats.receivedCount()).isEqualTo(1_000);
        assertThat(stats.invalidCount()).isEqualTo(100);
        assertThat(stats.duplicateCount()).isEqualTo(200);
        assertThat(stats.savedCount()).isEqualTo(700);
        assertThat(articleCount()).isEqualTo(800);
        assertThat(duplicateUrlGroupCount()).isZero();

        System.out.printf(
                "COLLECTION_BATCH provider=news-api mode=mixed received=%d invalid=%d duplicate=%d saved=%d statements=%d elapsedMs=%.2f rowsPerSecond=%.2f%n",
                stats.receivedCount(),
                stats.invalidCount(),
                stats.duplicateCount(),
                stats.savedCount(),
                statements,
                elapsedMs,
                rowsPerSecond(stats.receivedCount(), elapsedMs)
        );
    }

    @Test
    void sequentialMockUpstreamMeasuresSlowAndFailedSourcePropagation() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        ExecutorService executor = Executors.newCachedThreadPool();
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> requestOrder = Collections.synchronizedList(new ArrayList<>());
        server.createContext("/v2/top-headlines",
                exchange -> handleMockUpstream(exchange, objectMapper, requestOrder));
        server.setExecutor(executor);
        server.start();

        try {
            String mockBaseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            List<Double> controlRuns = measureMockUpstreamScenario(
                    mockBaseUrl,
                    requestOrder,
                    List.of("fast-a", "fast-c", "failure", "fast-b"),
                    "control"
            );
            List<Double> delayedRuns = measureMockUpstreamScenario(
                    mockBaseUrl,
                    requestOrder,
                    List.of("fast-a", "slow", "failure", "fast-b"),
                    "delayed"
            );
            double controlMedianMs = median(controlRuns);
            double delayedMedianMs = median(delayedRuns);
            System.out.printf(
                    "COLLECTION_UPSTREAM_COMPARISON requests=4 saved=75 failures=1 controlElapsedMs=%.2f delayedElapsedMs=%.2f slowDelayMs=300 deltaMs=%.2f%n",
                    controlMedianMs,
                    delayedMedianMs,
                    delayedMedianMs - controlMedianMs
            );
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private BatchMeasurement measureNewsBatch(int size, int run) {
        clearTables();
        NewsApiService service = newsApiService(mock(NewsFetchConfig.class));
        List<NewsApiArticleDto> articles = validNewsArticles(size, "news-" + size + "-" + run);

        statistics.clear();
        long newStartedAt = System.nanoTime();
        CollectionBatchStats newStats = service.processArticlesWithStats(articles, "us", "general");
        double newElapsedMs = elapsedMs(newStartedAt);
        long newStatements = statistics.getPrepareStatementCount();

        assertThat(newStats.savedCount()).isEqualTo(size);
        assertThat(newStats.invalidCount()).isZero();
        assertThat(newStats.duplicateCount()).isZero();
        assertThat(articleCount()).isEqualTo(size);

        statistics.clear();
        long rerunStartedAt = System.nanoTime();
        CollectionBatchStats rerunStats = service.processArticlesWithStats(articles, "us", "general");
        double rerunElapsedMs = elapsedMs(rerunStartedAt);
        long rerunStatements = statistics.getPrepareStatementCount();

        assertThat(rerunStats.savedCount()).isZero();
        assertThat(rerunStats.duplicateCount()).isEqualTo(size);
        assertThat(articleCount()).isEqualTo(size);
        assertThat(duplicateUrlGroupCount()).isZero();

        return new BatchMeasurement(
                newElapsedMs, newStatements, rerunElapsedMs, rerunStatements
        );
    }

    private BatchMeasurement measureRssBatch(int size, int run) {
        clearTables();
        RssNewsService service = rssNewsService();
        Elements items = rssItems(size, "rss-" + size + "-" + run);

        statistics.clear();
        long newStartedAt = System.nanoTime();
        CollectionBatchStats newStats = service.processItemsWithStats(RSS_FEED, items);
        double newElapsedMs = elapsedMs(newStartedAt);
        long newStatements = statistics.getPrepareStatementCount();

        assertThat(newStats.savedCount()).isEqualTo(size);
        assertThat(newStats.invalidCount()).isZero();
        assertThat(newStats.duplicateCount()).isZero();
        assertThat(articleCount()).isEqualTo(size);

        statistics.clear();
        long rerunStartedAt = System.nanoTime();
        CollectionBatchStats rerunStats = service.processItemsWithStats(RSS_FEED, items);
        double rerunElapsedMs = elapsedMs(rerunStartedAt);
        long rerunStatements = statistics.getPrepareStatementCount();

        assertThat(rerunStats.savedCount()).isZero();
        assertThat(rerunStats.duplicateCount()).isEqualTo(size);
        assertThat(articleCount()).isEqualTo(size);
        assertThat(duplicateUrlGroupCount()).isZero();

        return new BatchMeasurement(
                newElapsedMs, newStatements, rerunElapsedMs, rerunStatements
        );
    }

    private void warmUpNewsApi() {
        NewsApiService service = newsApiService(mock(NewsFetchConfig.class));
        service.processArticlesWithStats(validNewsArticles(20, "warmup-news"), "us", "general");
        clearTables();
    }

    private void warmUpRss() {
        rssNewsService().processItemsWithStats(RSS_FEED, rssItems(20, "warmup-rss"));
        clearTables();
    }

    private List<NewsApiArticleDto> validNewsArticles(int count, String prefix) {
        List<NewsApiArticleDto> articles = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            articles.add(newsArticle(
                    "https://fixture.example/" + prefix + "/" + index,
                    prefix + " article " + index,
                    "Fixture Source " + (index % 10),
                    true
            ));
        }
        return articles;
    }

    private List<NewsApiArticleDto> mixedNewsArticles() {
        List<NewsApiArticleDto> articles = new ArrayList<>(1_000);
        articles.addAll(validNewsArticles(700, "mixed-new"));
        articles.addAll(validNewsArticles(100, "existing"));
        for (int index = 0; index < 100; index++) {
            articles.add(newsArticle(
                    "https://fixture.example/mixed-new/" + index,
                    "duplicate article " + index,
                    "Fixture Source " + (index % 10),
                    true
            ));
        }
        for (int index = 0; index < 100; index++) {
            articles.add(newsArticle(
                    "",
                    "invalid article " + index,
                    "Fixture Source " + (index % 10),
                    false
            ));
        }
        return articles;
    }

    private NewsApiArticleDto newsArticle(
            String url,
            String title,
            String sourceName,
            boolean valid
    ) {
        NewsApiSourceDto source = new NewsApiSourceDto();
        source.setName(sourceName);

        NewsApiArticleDto article = new NewsApiArticleDto();
        article.setAuthor("fixture-author");
        article.setTitle(title);
        article.setDescription("fixture-description");
        article.setContent("fixture-content");
        article.setUrl(url);
        article.setUrlToImage("https://fixture.example/image.jpg");
        article.setPublishedAt(valid ? "2026-07-25T10:00:00Z" : null);
        article.setSource(source);
        return article;
    }

    private Elements rssItems(int count, String prefix) {
        StringBuilder xml = new StringBuilder("<rss><channel>");
        for (int index = 0; index < count; index++) {
            xml.append("<item>")
                    .append("<link>https://fixture.example/").append(prefix).append("/").append(index).append("</link>")
                    .append("<title>").append(prefix).append(" article ").append(index).append("</title>")
                    .append("<pubDate>Sat, 25 Jul 2026 10:00:00 GMT</pubDate>")
                    .append("<description>fixture-description</description>")
                    .append("</item>");
        }
        xml.append("</channel></rss>");
        return Jsoup.parse(xml.toString(), "", Parser.xmlParser()).select("item");
    }

    private NewsApiService newsApiService(NewsFetchConfig config) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1_000);
        requestFactory.setReadTimeout(1_000);
        NewsApiService service = new NewsApiService(
                new RestTemplate(requestFactory),
                articleRepository,
                sourceRepository,
                mock(ArticleService.class),
                sourceService,
                config
        );
        ReflectionTestUtils.setField(service, "apiKey", "fixture-api-key");
        return service;
    }

    private RssNewsService rssNewsService() {
        return new RssNewsService(
                articleRepository,
                sourceService,
                mock(RssFeedConfig.class)
        );
    }

    private List<Double> measureMockUpstreamScenario(
            String mockBaseUrl,
            List<String> requestOrder,
            List<String> categories,
            String scenario
    ) {
        List<Double> elapsedRuns = new ArrayList<>();
        for (int run = 1; run <= MEASUREMENT_RUNS; run++) {
            clearTables();
            requestOrder.clear();
            NewsApiService service = newsApiService(mockUpstreamConfig(categories));
            ReflectionTestUtils.setField(service, "newsApiBaseUrl", mockBaseUrl);
            statistics.clear();
            long startedAt = System.nanoTime();

            service.fetchTopHeadlines(false);

            double elapsedMs = elapsedMs(startedAt);
            long statements = statistics.getPrepareStatementCount();
            elapsedRuns.add(elapsedMs);
            assertThat(requestOrder).containsExactlyElementsOf(categories);
            assertThat(articleCount()).isEqualTo(75);
            assertThat(duplicateUrlGroupCount()).isZero();
            System.out.printf(
                    "COLLECTION_UPSTREAM scenario=%s run=%d requests=%d saved=75 failures=1 statements=%d elapsedMs=%.2f%n",
                    scenario,
                    run,
                    requestOrder.size(),
                    statements,
                    elapsedMs
            );
        }
        return elapsedRuns;
    }

    private NewsFetchConfig mockUpstreamConfig(List<String> categories) {
        NewsFetchConfig config = mock(NewsFetchConfig.class);
        when(config.getCountries()).thenReturn(List.of("us"));
        when(config.getCategories()).thenReturn(categories);
        when(config.getPageSize()).thenReturn(25);
        when(config.getMaxRequestsPerRun()).thenReturn(10);
        when(config.getRequestDelayMs()).thenReturn(0L);
        return config;
    }

    private void handleMockUpstream(
            HttpExchange exchange,
            ObjectMapper objectMapper,
            List<String> requestOrder
    ) throws IOException {
        String category = queryParameters(exchange).get("category");
        requestOrder.add(category);
        if ("slow".equals(category)) {
            sleep(300);
        }
        if ("failure".equals(category)) {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
            return;
        }

        NewsApiResponseDto response = new NewsApiResponseDto();
        response.setStatus("ok");
        response.setTotalResults(25);
        response.setArticles(validNewsArticles(25, "upstream-" + category));
        byte[] body = objectMapper.writeValueAsBytes(response);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try {
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private Map<String, String> queryParameters(HttpExchange exchange) {
        return java.util.Arrays.stream(exchange.getRequestURI().getRawQuery().split("&"))
                .map(parameter -> parameter.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> pair.length == 2
                                ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                                : ""
                ));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void printMedian(String provider, int size, List<BatchMeasurement> measurements) {
        double newElapsedMedian = medianDouble(measurements, BatchMeasurement::newElapsedMs);
        long newStatementsMedian = medianLong(measurements, BatchMeasurement::newStatements);
        double rerunElapsedMedian = medianDouble(measurements, BatchMeasurement::rerunElapsedMs);
        long rerunStatementsMedian = medianLong(measurements, BatchMeasurement::rerunStatements);
        System.out.printf(
                "COLLECTION_BATCH_MEDIAN provider=%s size=%d newSaved=%d newStatements=%d newElapsedMs=%.2f newRowsPerSecond=%.2f rerunSaved=0 rerunDuplicates=%d rerunStatements=%d rerunElapsedMs=%.2f rerunRowsPerSecond=%.2f%n",
                provider,
                size,
                size,
                newStatementsMedian,
                newElapsedMedian,
                rowsPerSecond(size, newElapsedMedian),
                size,
                rerunStatementsMedian,
                rerunElapsedMedian,
                rowsPerSecond(size, rerunElapsedMedian)
        );
    }

    private double medianDouble(
            List<BatchMeasurement> measurements,
            ToDoubleFunction<BatchMeasurement> extractor
    ) {
        return measurements.stream()
                .mapToDouble(extractor)
                .sorted()
                .skip(measurements.size() / 2)
                .findFirst()
                .orElseThrow();
    }

    private long medianLong(
            List<BatchMeasurement> measurements,
            ToLongFunction<BatchMeasurement> extractor
    ) {
        return measurements.stream()
                .mapToLong(extractor)
                .sorted()
                .skip(measurements.size() / 2)
                .findFirst()
                .orElseThrow();
    }

    private double median(List<Double> values) {
        return values.stream()
                .mapToDouble(Double::doubleValue)
                .sorted()
                .skip(values.size() / 2)
                .findFirst()
                .orElseThrow();
    }

    private double elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000.0;
    }

    private double rowsPerSecond(int rows, double elapsedMs) {
        return rows * 1_000.0 / elapsedMs;
    }

    private int articleCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM article", Integer.class);
        return count == null ? 0 : count;
    }

    private int duplicateUrlGroupCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" +
                        "SELECT url_hash FROM article GROUP BY url_hash HAVING COUNT(*) > 1" +
                        ") duplicate_urls",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private void clearTables() {
        jdbcTemplate.update("DELETE FROM article");
        jdbcTemplate.update("DELETE FROM source");
        if (statistics != null) {
            statistics.clear();
        }
    }

    private record BatchMeasurement(
            double newElapsedMs,
            long newStatements,
            double rerunElapsedMs,
            long rerunStatements
    ) {
    }
}
