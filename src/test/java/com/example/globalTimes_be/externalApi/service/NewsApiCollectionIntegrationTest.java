package com.example.globalTimes_be.externalApi.service;

import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.article.service.ArticleService;
import com.example.globalTimes_be.domain.source.repository.SourceRepository;
import com.example.globalTimes_be.domain.source.service.SourceService;
import com.example.globalTimes_be.externalApi.config.NewsFetchConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ExtendWith(OutputCaptureExtension.class)
@Import(SourceService.class)
class NewsApiCollectionIntegrationTest {

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

    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private HttpServer mockNewsApi;
    private ExecutorService mockExecutor;

    @BeforeEach
    void startMockNewsApi() throws IOException {
        mockNewsApi = HttpServer.create(new InetSocketAddress(0), 0);
        mockNewsApi.createContext("/v2/top-headlines", this::handleTopHeadlines);
        mockExecutor = Executors.newCachedThreadPool();
        mockNewsApi.setExecutor(mockExecutor);
        mockNewsApi.start();
    }

    @AfterEach
    void cleanUp() {
        if (mockNewsApi != null) {
            mockNewsApi.stop(0);
        }
        if (mockExecutor != null) {
            mockExecutor.shutdownNow();
        }
        jdbcTemplate.update("DELETE FROM article");
        jdbcTemplate.update("DELETE FROM source");
        requestCounts.clear();
    }

    @Test
    void mixedUpstreamFailuresDoNotBlockSuccessfulArticlesAndRerunIsIdempotent(CapturedOutput output) {
        NewsApiService service = newsApiService();

        service.fetchTopHeadlines(false);

        assertThat(articleCount()).isEqualTo(2);
        assertThat(exactUrlCount("https://news.example/general")).isEqualTo(1);
        assertThat(exactUrlCount("https://news.example/technology")).isEqualTo(1);

        service.fetchTopHeadlines(false);

        assertThat(articleCount()).isEqualTo(2);
        assertThat(duplicateUrlGroupCount()).isZero();
        assertThat(requestCounts).containsOnlyKeys("general", "business", "science", "technology");
        assertThat(requestCounts).allSatisfy((category, count) -> assertThat(count).hasValue(2));
        assertThat(output).contains("[수집 오류] us/business 처리 중 오류 발생");
        assertThat(output).contains("[수집 오류] us/science 처리 중 오류 발생");
    }

    private NewsApiService newsApiService() {
        NewsFetchConfig config = mock(NewsFetchConfig.class);
        when(config.getCountries()).thenReturn(List.of("us"));
        when(config.getCategories()).thenReturn(List.of("general", "business", "science", "technology"));
        when(config.getPageSize()).thenReturn(10);
        when(config.getMaxRequestsPerRun()).thenReturn(10);
        when(config.getRequestDelayMs()).thenReturn(0L);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1_000);
        requestFactory.setReadTimeout(500);

        NewsApiService service = new NewsApiService(
                new RestTemplate(requestFactory),
                articleRepository,
                sourceRepository,
                mock(ArticleService.class),
                sourceService,
                config
        );
        ReflectionTestUtils.setField(service, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(service, "newsApiBaseUrl", mockBaseUrl());
        return service;
    }

    private void handleTopHeadlines(HttpExchange exchange) throws IOException {
        String category = queryParameters(exchange).get("category");
        requestCounts.computeIfAbsent(category, ignored -> new AtomicInteger()).incrementAndGet();

        if ("business".equals(category)) {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
            return;
        }
        if ("science".equals(category)) {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exchange.close();
                return;
            }
        }

        byte[] response = newsApiResponse(category).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try {
            exchange.getResponseBody().write(response);
        } finally {
            exchange.close();
        }
    }

    private Map<String, String> queryParameters(HttpExchange exchange) {
        return Arrays.stream(exchange.getRequestURI().getRawQuery().split("&"))
                .map(parameter -> parameter.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> pair.length == 2
                                ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                                : ""
                ));
    }

    private String newsApiResponse(String category) {
        return """
                {
                  "status": "ok",
                  "totalResults": 1,
                  "articles": [
                    {
                      "source": {"id": null, "name": "Mock News"},
                      "author": "author",
                      "title": "%s title",
                      "description": "description",
                      "url": "https://news.example/%s",
                      "urlToImage": "https://news.example/image.jpg",
                      "publishedAt": "2026-07-18T10:00:00Z",
                      "content": "content"
                    }
                  ]
                }
                """.formatted(category, category);
    }

    private String mockBaseUrl() {
        return "http://127.0.0.1:" + mockNewsApi.getAddress().getPort();
    }

    private int articleCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM article", Integer.class);
        return count == null ? 0 : count;
    }

    private int exactUrlCount(String url) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM article WHERE BINARY url = BINARY ?",
                Integer.class,
                url
        );
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
}
