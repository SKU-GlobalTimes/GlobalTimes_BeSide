package com.example.globalTimes_be.domain.trend.service;

import com.example.globalTimes_be.domain.trend.exception.TrendErrorStatus;
import com.example.globalTimes_be.global.exception.BaseException;
import com.example.globalTimes_be.global.redis.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final WebClient openAiWebClient;
    private final RedisUtil redisUtil;

    @Value("${trend.summary-cache-ttl-seconds:21600}")
    private long cacheTtlSeconds;

    public String summarizeTrendArticle(String content, String language, String url) {
        if (cacheTtlSeconds > 0 && url != null) {
            String cacheKey = CACHE_KEY_PREFIX + hashUrl(url) + ":" + language;
            try {
                String cached = redisUtil.getData(cacheKey);
                if (cached != null) {
                    log.debug("[트렌드 요약] 캐시 히트 url={}", url);
                    return cached;
                }
            } catch (Exception e) {
                log.warn("[트렌드 요약] 캐시 조회 실패, GPT 호출: {}", e.getMessage());
            }

            String summary = callGpt(content, language);

            try {
                redisUtil.setData(cacheKey, summary, cacheTtlSeconds);
            } catch (Exception e) {
                log.warn("[트렌드 요약] 캐시 저장 실패 (무시): {}", e.getMessage());
            }
            return summary;
        }

        return callGpt(content, language);
    }

    private String callGpt(String content, String language) {
        String summary = openAiWebClient.post()
                .uri("/chat/completions")
                .bodyValue(createRequestBody(content, language))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> extractContent(response))
                .block();
        return summary;
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

    // OpenAI 요청 본문 생성 (기사 요약)
    private Map<String, Object> createRequestBody(String content, String language) {
        if (content == null || language == null) {
            throw new IllegalArgumentException("content나 language는 null일 수 없습니다.");
        }

        return Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "system", "content", "이 기사를 " + language + "로 2줄 요약해줘."),
                        Map.of("role", "user", "content", content)
                ),
                "stream", false  // 🔹 스트리밍 비활성화
        );
    }

    // OpenAI 응답에서 'content' 추출
    private String extractContent(Map<String, Object> response) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        }
        throw new BaseException(TrendErrorStatus._GPT_ERROR.getResponse());
    }
}
