package com.example.globalTimes_be.domain.trend.service;

import com.example.globalTimes_be.global.exception.BaseException;
import com.example.globalTimes_be.global.redis.RedisUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrendAiServiceTest {

    private HttpServer mockServer;
    private RedisUtil redisUtil;
    private TrendAiService trendAiService;
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile int responseStatus;
    private volatile long responseDelayMs;
    private volatile String responseBody;
    private volatile String requestBody;

    @BeforeEach
    void setUp() throws IOException {
        responseStatus = 200;
        responseDelayMs = 0;
        responseBody = """
                {"candidates":[{"content":{"parts":[{"text":"mock trend summary"}]}}]}
                """;
        requestBody = "";
        requestCount.set(0);

        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.createContext("/v1beta/models/", this::handleGeminiRequest);
        mockServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + mockServer.getAddress().getPort())
                .build();
        redisUtil = mock(RedisUtil.class);
        trendAiService = new TrendAiService(webClient, redisUtil);
        ReflectionTestUtils.setField(trendAiService, "geminiApiKey", "dummy");
        ReflectionTestUtils.setField(trendAiService, "geminiTimeoutMs", 5000L);
        ReflectionTestUtils.setField(trendAiService, "cacheTtlSeconds", 0L);
    }

    @AfterEach
    void tearDown() {
        mockServer.stop(0);
    }

    @Test
    void summarizeTrendArticle_restoresPromptAndReturnsSummary() {
        String result = trendAiService.summarizeTrendArticle(
                "trend article content",
                "Korean",
                "https://example.com/trend"
        );

        assertThat(result).isEqualTo("mock trend summary");
        assertThat(requestBody)
                .contains("trend article content")
                .contains("Korean")
                .contains("2 sentences")
                .doesNotContain("??");
    }

    @Test
    void summarizeTrendArticle_returnsCachedSummaryWithoutGeminiCall() {
        ReflectionTestUtils.setField(trendAiService, "cacheTtlSeconds", 60L);
        when(redisUtil.getData(anyString())).thenReturn("cached trend summary");

        String result = trendAiService.summarizeTrendArticle(
                "trend article content",
                "English",
                "https://example.com/trend"
        );

        assertThat(result).isEqualTo("cached trend summary");
        assertThat(requestCount).hasValue(0);
        verify(redisUtil, never()).setData(anyString(), anyString(), anyLong());
    }

    @Test
    void summarizeTrendArticle_fallsBackToGeminiWhenCacheReadFails() {
        ReflectionTestUtils.setField(trendAiService, "cacheTtlSeconds", 60L);
        when(redisUtil.getData(anyString())).thenThrow(new IllegalStateException("redis unavailable"));

        String result = trendAiService.summarizeTrendArticle(
                "trend article content",
                "English",
                "https://example.com/trend"
        );

        assertThat(result).isEqualTo("mock trend summary");
        assertThat(requestCount).hasValue(1);
    }

    @Test
    void summarizeTrendArticle_preservesSummaryWhenCacheWriteFails() {
        ReflectionTestUtils.setField(trendAiService, "cacheTtlSeconds", 60L);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(redisUtil).setData(anyString(), anyString(), anyLong());

        String result = trendAiService.summarizeTrendArticle(
                "trend article content",
                "English",
                "https://example.com/trend"
        );

        assertThat(result).isEqualTo("mock trend summary");
        assertThat(requestCount).hasValue(1);
    }

    @Test
    void summarizeTrendArticle_mapsGeminiFailureToBadGateway() {
        responseStatus = 500;
        responseBody = "{\"error\":{\"message\":\"mock failure\"}}";

        assertThatThrownBy(() -> trendAiService.summarizeTrendArticle(
                "trend article content", "English", null))
                .isInstanceOfSatisfying(BaseException.class, ex ->
                        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void summarizeTrendArticle_mapsTimeoutToGatewayTimeout() {
        responseDelayMs = 250;
        ReflectionTestUtils.setField(trendAiService, "geminiTimeoutMs", 50L);

        assertThatThrownBy(() -> trendAiService.summarizeTrendArticle(
                "trend article content", "English", null))
                .isInstanceOfSatisfying(BaseException.class, ex ->
                        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
    }

    @Test
    void summarizeTrendArticle_keepsInvalidResponseAsInternalServerError() {
        responseBody = "{}";

        assertThatThrownBy(() -> trendAiService.summarizeTrendArticle(
                "trend article content", "English", null))
                .isInstanceOfSatisfying(BaseException.class, ex ->
                        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private void handleGeminiRequest(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        if (responseDelayMs > 0) {
            try {
                Thread.sleep(responseDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
