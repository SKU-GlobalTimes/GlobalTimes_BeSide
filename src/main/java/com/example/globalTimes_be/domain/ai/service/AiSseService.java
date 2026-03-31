package com.example.globalTimes_be.domain.ai.service;

import com.example.globalTimes_be.domain.chat.service.ChatHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class AiSseService {

    // Gemini (?? ??)
    @Qualifier("geminiWebClient")
    private final WebClient geminiWebClient;

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    private static final String GEMINI_MODEL = "gemini-2.5-flash";

    private final ChatHistoryService chatHistoryService;

    public SseEmitter summarizeContent(String crawledContent, String language) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        Map<String, Object> body = createGeminiRequestBody(
                "? ??? " + language + "? ????.",
                crawledContent
        );
        processGeminiStreaming(emitter, body, null, null, null);
        return emitter;
    }

    public SseEmitter askGPT(String crawledContent, String question, Long userId, Long articleId) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        Map<String, Object> body = createGeminiRequestBody(
                "?? ?? ??? ???? ??? ??? ???? ????.",
                "?? ??:\n" + crawledContent + "\n\n??? ??:\n" + question
        );
        processGeminiStreaming(emitter, body, question, userId, articleId);
        return emitter;
    }

    private Map<String, Object> createGeminiRequestBody(String systemPrompt, String userMessage) {
        return Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))
                ),
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", userMessage)))
                )
        );
    }

    @SuppressWarnings("unchecked")
    private void processGeminiStreaming(SseEmitter emitter, Map<String, Object> requestBody,
                                        String question, Long userId, Long articleId) {
        StringBuilder resultBuilder = new StringBuilder();

        geminiWebClient.post()
                .uri("/v1beta/models/" + GEMINI_MODEL + ":streamGenerateContent?alt=sse&key=" + geminiApiKey)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(data -> {
                    String jsonPart = data.trim();
                    if (jsonPart.isEmpty()) return;

                    try {
                        Map<String, Object> response = new ObjectMapper().readValue(jsonPart, Map.class);
                        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                        if (candidates == null || candidates.isEmpty()) return;

                        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                        if (content == null) return;

                        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                        if (parts == null || parts.isEmpty()) return;

                        String text = (String) parts.get(0).get("text");
                        if (text != null) {
                            resultBuilder.append(text);
                            emitter.send(resultBuilder.toString());
                        }
                    } catch (Exception e) {
                        log.warn("[Gemini SSE] JSON ?? ??: {}", e.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    // ???? ?? ? ???? ??
                    if (userId != null && articleId != null && question != null) {
                        chatHistoryService.save(userId, articleId, question, resultBuilder.toString());
                    }
                    try {
                        emitter.complete();
                    } catch (Exception e) {
                        log.warn("[Gemini SSE] emitter complete ??: {}", e.getMessage());
                    }
                })
                .doOnError(error -> {
                    log.error("[Gemini SSE] ?? ? ?? ??", error);
                    emitter.completeWithError(error);
                })
                .subscribe();
    }


    // GPT (??? / ?? ??)
    // private final WebClient openAiWebClient;
    //
    // public SseEmitter summarizeContent(String crawledContent, String language) {
    //     SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
    //     processStreamingRequest(emitter, createRequestBody(crawledContent, language), null, null, null);
    //     return emitter;
    // }
    //
    // public SseEmitter askGPT(String crawledContent, String question, Long userId, Long articleId) {
    //     SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
    //     processStreamingRequest(emitter, createQuestionRequestBody(crawledContent, question), question, userId, articleId);
    //     return emitter;
    // }
    //
    // private void processStreamingRequest(SseEmitter emitter, Map<String, Object> requestBody,
    //                                      String question, Long userId, Long articleId) {
    //     StringBuilder resultBuilder = new StringBuilder();
    //     openAiWebClient.post()
    //             .uri("/chat/completions")
    //             .accept(MediaType.TEXT_EVENT_STREAM)
    //             .bodyValue(requestBody)
    //             .retrieve()
    //             .bodyToFlux(String.class)
    //             .doOnNext(data -> {
    //                 String jsonPart = data.trim();
    //                 if ("[DONE]".equals(jsonPart)) {
    //                     if (userId != null && articleId != null && question != null) {
    //                         chatHistoryService.save(userId, articleId, question, resultBuilder.toString());
    //                     }
    //                     emitter.complete();
    //                     return;
    //                 }
    //                 try {
    //                     Map response = new ObjectMapper().readValue(jsonPart, Map.class);
    //                     List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
    //                     if (choices != null && !choices.isEmpty()) {
    //                         Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
    //                         if (delta != null && delta.containsKey("content")) {
    //                             String contentText = (String) delta.get("content");
    //                             resultBuilder.append(contentText);
    //                             emitter.send(resultBuilder.toString());
    //                         }
    //                     }
    //                 } catch (Exception e) {
    //                     log.warn("JSON ?? ?? ??!", e);
    //                     emitter.completeWithError(e);
    //                 }
    //             })
    //             .doOnError(error -> {
    //                 log.error("OpenAI SSE ?? ? ?? ??", error);
    //                 emitter.completeWithError(error);
    //             })
    //             .subscribe();
    // }
    //
    // private Map<String, Object> createRequestBody(String crawledContent, String language) {
    //     return Map.of(
    //             "model", "gpt-4o-mini",
    //             "messages", List.of(
    //                     Map.of("role", "system", "content", "? ??? " + language + "? ????."),
    //                     Map.of("role", "user", "content", crawledContent)
    //             ),
    //             "stream", true
    //     );
    // }
    //
    // private Map<String, Object> createQuestionRequestBody(String crawledContent, String question) {
    //     return Map.of(
    //             "model", "gpt-4o-mini",
    //             "messages", List.of(
    //                     Map.of("role", "system", "content", "?? ?? ??? ???? ??? ??? ???? ????."),
    //                     Map.of("role", "user", "content", "?? ??:\n" + crawledContent),
    //                     Map.of("role", "user", "content", "??? ??:\n" + question)
    //             ),
    //             "stream", true
    //     );
    // }
}
