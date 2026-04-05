package com.example.globalTimes_be.domain.ai.service;

import com.example.globalTimes_be.domain.detail.exception.DetailErrorStatus;
import com.example.globalTimes_be.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class AiService {

    // Gemini (?? ??)
    @Qualifier("geminiWebClient")
    private final WebClient geminiWebClient;

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    private static final String GEMINI_MODEL = "gemini-2.5-flash";

    public String summarizeArticle(String crawledContent, String language) {
        return geminiWebClient.post()
                .uri("/v1beta/models/" + GEMINI_MODEL + ":generateContent?key=" + geminiApiKey)
                .bodyValue(createGeminiRequestBody(crawledContent, language))
                .retrieve()
                .onStatus(
                        status -> !status.is2xxSuccessful(),
                        response -> response.bodyToMono(String.class)
                                .doOnNext(body -> log.error("[Gemini] API ?? ??: {}", body))
                                .then(Mono.error(new BaseException(DetailErrorStatus._GPT_ERROR.getResponse())))
                )
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(90))
                .onErrorMap(
                        ex -> !(ex instanceof BaseException),
                        ex -> {
                            log.error("[Gemini] ?? ??: {}", ex.getMessage());
                            return new BaseException(DetailErrorStatus._GPT_ERROR.getResponse());
                        }
                )
                .map(this::extractGeminiContent)
                .block();
    }

    private Map<String, Object> createGeminiRequestBody(String crawledContent, String language) {
        String systemPrompt = "You are a news summarization assistant. Summarize the following article content in " + language + ". "
                + "The summary should be concise (3-5 sentences), written in " + language + ", and capture the key points of the article.";

        return Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))
                ),
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", crawledContent)))
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
        throw new BaseException(DetailErrorStatus._GPT_ERROR.getResponse());
    }


    // GPT (??? / ?? ??)
    // private final WebClient openAiWebClient;
    //
    // public String summarizeArticle(String crawledContent, String language) {
    //     return openAiWebClient.post()
    //             .uri("/chat/completions")
    //             .bodyValue(createGptRequestBody(crawledContent, language))
    //             .retrieve()
    //             .onStatus(
    //                     status -> !status.is2xxSuccessful(),
    //                     response -> Mono.error(new BaseException(DetailErrorStatus._GPT_ERROR.getResponse()))
    //             )
    //             .bodyToMono(Map.class)
    //             .timeout(Duration.ofSeconds(10))
    //             .onErrorMap(
    //                     ex -> !(ex instanceof BaseException),
    //                     ex -> new BaseException(DetailErrorStatus._GPT_ERROR.getResponse())
    //             )
    //             .map(response -> extractGptContent(response))
    //             .block();
    // }
    //
    // private Map<String, Object> createGptRequestBody(String crawledContent, String language) {
    //     return Map.of(
    //             "model", "gpt-4o-mini",
    //             "messages", List.of(
    //                     Map.of("role", "system", "content", "? ??? " + language + "? ????."),
    //                     Map.of("role", "user", "content", crawledContent)
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
    //     throw new BaseException(DetailErrorStatus._GPT_ERROR.getResponse());
    // }
}
