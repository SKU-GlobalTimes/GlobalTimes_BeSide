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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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

    private final RedisUtil redisUtil;

    @Value("${trend.summary-cache-ttl-seconds:21600}")
    private long cacheTtlSeconds;

    public String summarizeTrendArticle(String content, String language, String url) {
        if (cacheTtlSeconds > 0 && url != null) {
            String cacheKey = CACHE_KEY_PREFIX + hashUrl(url) + ":" + language;
            try {
                String cached = redisUtil.getData(cacheKey);
                if (cached != null) {
                    log.debug("[??? ??] ?? ?? url={}", url);
                    return cached;
                }
            } catch (Exception e) {
                log.warn("[??? ??] ?? ?? ??, Gemini ??: {}", e.getMessage());
            }

            String summary = callGemini(content, language);

            try {
                redisUtil.setData(cacheKey, summary, cacheTtlSeconds);
            } catch (Exception e) {
                log.warn("[??? ??] ?? ?? ?? (??): {}", e.getMessage());
            }
            return summary;
        }

        return callGemini(content, language);
    }

    private String callGemini(String content, String language) {
        Map<String, Object> requestBody = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", "? ??? " + language + "? 2?? ????."))
                ),
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", content)))
                )
        );

        return geminiWebClient.post()
                .uri("/v1beta/models/" + GEMINI_MODEL + ":generateContent?key=" + geminiApiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractGeminiContent)
                .block();
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


    // GPT (??? / ?? ??)
    // private final WebClient openAiWebClient;
    //
    // private String callGpt(String content, String language) {
    //     return openAiWebClient.post()
    //             .uri("/chat/completions")
    //             .bodyValue(createGptRequestBody(content, language))
    //             .retrieve()
    //             .bodyToMono(Map.class)
    //             .map(response -> extractGptContent(response))
    //             .block();
    // }
    //
    // private Map<String, Object> createGptRequestBody(String content, String language) {
    //     return Map.of(
    //             "model", "gpt-4o-mini",
    //             "messages", List.of(
    //                     Map.of("role", "system", "content", "? ??? " + language + "? 2?? ????."),
    //                     Map.of("role", "user", "content", content)
    //             ),
    //             "stream", false
    //     );
    // }
    //
    // private String extractGptContent(Map<String, Object> response) {
    //     List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
    //     if (choices != null && !choices.isEmpty()) {
    //         Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
    //         return (String) message.get("content");
    //     }
    //     throw new BaseException(TrendErrorStatus._GPT_ERROR.getResponse());
    // }
}
