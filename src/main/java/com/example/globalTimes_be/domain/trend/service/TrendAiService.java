package com.example.globalTimes_be.domain.trend.service;

import com.example.globalTimes_be.domain.trend.exception.TrendErrorStatus;
import com.example.globalTimes_be.global.exception.BaseException;
import com.example.globalTimes_be.global.redis.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
@Service
public class TrendAiService {

    private static final String CACHE_KEY_PREFIX = "trend:summary:";
    private static final String GEMINI_MODEL = "gemini-2.5-flash";

    // Gemini (?? ??)
    @Qualifier("geminiWebClient")
    private final WebClient geminiWebClient;

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.timeout-ms:10000}")
    private long geminiTimeoutMs;

    private final RedisUtil redisUtil;

    @Value("${trend.summary-cache-ttl-seconds:21600}")
    private long cacheTtlSeconds;

    public String summarizeTrendArticle(String content, String language, String url) {
        if (cacheTtlSeconds > 0 && url != null) {
            String cacheKey = CACHE_KEY_PREFIX + hashUrl(url) + ":" + language;
            try {
                String cached = redisUtil.getData(cacheKey);
                if (cached != null) {
                    log.debug("[Trend summary] cacheHit=true url={}", url);
                    return cached;
                }
            } catch (Exception e) {
                log.warn("[Trend summary] cacheReadFailed=true fallback=gemini type={}",
                        e.getClass().getSimpleName());
            }

            String summary = callGemini(content, language);

            try {
                redisUtil.setData(cacheKey, summary, cacheTtlSeconds);
            } catch (Exception e) {
                log.warn("[Trend summary] cacheWriteFailed=true responsePreserved=true type={}",
                        e.getClass().getSimpleName());
            }
            return summary;
        }

        return callGemini(content, language);
    }

    private String callGemini(String content, String language) {
        return geminiWebClient.post()
                .uri("/v1beta/models/" + GEMINI_MODEL + ":generateContent?key=" + geminiApiKey)
                .bodyValue(createGeminiRequestBody(content, language))
                .retrieve()
                .onStatus(
                        status -> !status.is2xxSuccessful(),
                        response -> {
                            log.warn("[Trend Gemini] upstreamError=true status={}",
                                    response.statusCode().value());
                            return response.releaseBody()
                                    .then(Mono.error(new BaseException(
                                            TrendErrorStatus._GEMINI_UPSTREAM_ERROR.getResponse()
                                    )));
                        }
                )
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(geminiTimeoutMs))
                .onErrorMap(
                        TimeoutException.class,
                        ex -> {
                            log.warn("[Trend Gemini] timeout=true timeoutMs={}", geminiTimeoutMs);
                            return new BaseException(TrendErrorStatus._GEMINI_TIMEOUT.getResponse());
                        }
                )
                .onErrorMap(
                        ex -> !(ex instanceof BaseException),
                        ex -> {
                            log.error("[Trend Gemini] internalError=true type={}",
                                    ex.getClass().getSimpleName());
                            return new BaseException(TrendErrorStatus._GPT_ERROR.getResponse());
                        }
                )
                .map(this::extractGeminiContent)
                .block();
    }

    private Map<String, Object> createGeminiRequestBody(String content, String language) {
        String systemPrompt = "You are a news summarization assistant. Summarize the following trend article content in "
                + language + ". The summary should be concise (2 sentences), written in "
                + language + ", and capture the key points of the article.";

        return Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))
                ),
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", content)))
                )
        );
    }

    @SuppressWarnings("unchecked")
    private String extractGeminiContent(Map<String, Object> response) {
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates != null && !candidates.isEmpty()) {
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            if (content != null) {
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    return (String) parts.get(0).get("text");
                }
            }
        }
        throw new BaseException(TrendErrorStatus._GPT_ERROR.getResponse());
    }

    private String hashUrl(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(url.hashCode());
        }
    }
}
