package com.example.globalTimes_be.domain.ai.service;

import com.example.globalTimes_be.global.exception.BaseException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiServiceTest {

    private HttpServer mockServer;
    private AiService aiService;
    private volatile int responseStatus;
    private volatile long responseDelayMs;
    private volatile String responseBody;

    @BeforeEach
    void setUp() throws IOException {
        responseStatus = 200;
        responseDelayMs = 0;
        responseBody = """
                {"candidates":[{"content":{"parts":[{"text":"mock summary"}]}}]}
                """;

        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.createContext("/v1beta/models/", this::handleGeminiRequest);
        mockServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + mockServer.getAddress().getPort())
                .build();
        aiService = new AiService(webClient);
        ReflectionTestUtils.setField(aiService, "geminiApiKey", "dummy");
        ReflectionTestUtils.setField(aiService, "geminiTimeoutMs", 5000L);
    }

    @AfterEach
    void tearDown() {
        mockServer.stop(0);
    }

    @Test
    void summarizeArticle_returnsSummaryForSuccessfulGeminiResponse() {
        String result = aiService.summarizeArticle("article content", "English");

        assertThat(result).isEqualTo("mock summary");
    }

    @Test
    void summarizeArticle_mapsGeminiFailureToBadGateway() {
        responseStatus = 500;
        responseBody = "{\"error\":{\"message\":\"mock failure\"}}";

        assertThatThrownBy(() -> aiService.summarizeArticle("article content", "English"))
                .isInstanceOfSatisfying(BaseException.class, ex ->
                        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void summarizeArticle_mapsTimeoutToGatewayTimeout() {
        responseDelayMs = 250;
        ReflectionTestUtils.setField(aiService, "geminiTimeoutMs", 50L);

        assertThatThrownBy(() -> aiService.summarizeArticle("article content", "English"))
                .isInstanceOfSatisfying(BaseException.class, ex ->
                        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
    }

    @Test
    void summarizeArticle_keepsInvalidResponseAsInternalServerError() {
        responseBody = "{}";

        assertThatThrownBy(() -> aiService.summarizeArticle("article content", "English"))
                .isInstanceOfSatisfying(BaseException.class, ex ->
                        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private void handleGeminiRequest(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();

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
