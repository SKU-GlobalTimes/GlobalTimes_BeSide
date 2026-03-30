package com.example.globalTimes_be.domain.ai.service;

import com.example.globalTimes_be.domain.chat.service.ChatHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class AiSseService {
    private final WebClient openAiWebClient;
    private final ChatHistoryService chatHistoryService;

    public SseEmitter summarizeContent(String crawledContent, String language) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        processStreamingRequest(emitter, createRequestBody(crawledContent, language), null, null, null);
        return emitter;
    }

    // 인증된 사용자: userId, articleId 전달 → 스트리밍 완료 후 히스토리 저장
    // 비인증 사용자: userId = null → 저장 생략
    public SseEmitter askGPT(String crawledContent, String question, Long userId, Long articleId) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        processStreamingRequest(emitter, createQuestionRequestBody(crawledContent, question), question, userId, articleId);
        return emitter;
    }

    private void processStreamingRequest(SseEmitter emitter, Map<String, Object> requestBody,
                                         String question, Long userId, Long articleId) {
        StringBuilder resultBuilder = new StringBuilder();

        openAiWebClient.post()
                .uri("/chat/completions")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(data -> {
                    String jsonPart = data.trim();

                    if ("[DONE]".equals(jsonPart)) {
                        // 스트리밍 완료 → 로그인 사용자면 히스토리 저장
                        if (userId != null && articleId != null && question != null) {
                            chatHistoryService.save(userId, articleId, question, resultBuilder.toString());
                        }
                        emitter.complete();
                        return;
                    }

                    try {
                        Map response = new ObjectMapper().readValue(jsonPart, Map.class);

                        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                            if (delta != null && delta.containsKey("content")) {
                                String contentText = (String) delta.get("content");
                                resultBuilder.append(contentText);

                                String currentText = resultBuilder.toString();
                                emitter.send(currentText);
                            }
                        }
                    } catch (IOException e) {
                        log.warn("JSON 파싱 오류 발생!", e);
                        emitter.completeWithError(e);
                    }
                })
                .doOnError(error -> {
                    log.error("OpenAI SSE 처리 중 오류 발생", error);
                    emitter.completeWithError(error);
                })
                .subscribe();
    }

    private Map<String, Object> createRequestBody(String crawledContent, String language) {
        return Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "system", "content", "이 기사를 " + language + "로 요약해줘."),
                        Map.of("role", "user", "content", crawledContent)
                ),
                "stream", true
        );
    }

    private Map<String, Object> createQuestionRequestBody(String crawledContent, String question) {
        return Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "system", "content", "다음 기사 내용을 참고하여 사용자 질문에 자세하게 답변해줘."),
                        Map.of("role", "user", "content", "기사 내용:\n" + crawledContent),
                        Map.of("role", "user", "content", "사용자 질문:\n" + question)
                ),
                "stream", true
        );
    }
}
