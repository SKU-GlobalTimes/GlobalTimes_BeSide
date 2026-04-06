package com.example.globalTimes_be.domain.ai.service;

import com.example.globalTimes_be.domain.chat.entity.ChatHistory;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class AiSseService {

    // Gemini (현재 활성)
    @Qualifier("geminiWebClient")
    private final WebClient geminiWebClient;

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${ai.context-window-size:10}")
    private int contextWindowSize;

    private static final String GEMINI_MODEL = "gemini-2.5-flash";

    private final ChatHistoryService chatHistoryService;

    public SseEmitter summarizeContent(String crawledContent, String language) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        Map<String, Object> body = createGeminiRequestBody(
                "이 기사를 " + language + "로 요약해줘.",
                crawledContent
        );
        processGeminiStreaming(emitter, body, null, null, null);
        return emitter;
    }

    public SseEmitter askGPT(String crawledContent, String question, Long userId, Long articleId) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);

        Map<String, Object> body;
        if (userId != null && articleId != null) {
            // 로그인 유저: 슬라이딩 윈도우로 이전 대화 내역 가져와 컨텍스트 구성
            List<ChatHistory> history = chatHistoryService.getRecentContext(userId, articleId, contextWindowSize);
            body = createContextualRequestBody(crawledContent, question, history);
            log.debug("[Gemini SSE] 컨텍스트 {}턴 포함 요청 - userId={}, articleId={}", history.size(), userId, articleId);
        } else {
            // 비로그인: 단일 질의
            // 기사 내용을 참고하되, 인사·감사 등 일상 대화에는 간결하게 응답
            body = createGeminiRequestBody(
                    "당신은 기사 내용을 기반으로 질문에 답하는 AI 어시스턴트입니다.\n" +
                    "- 기사 내용과 관련된 질문이면 기사를 주요 참고 자료로 활용하되, 기사에 없는 정보는 일반 지식을 활용해 자세하게 답변하세요.\n" +
                    "- 기사와 무관한 질문(인사, 감사 표현 등)에는 2~3문장 이내로 간결하게 응답하세요.\n" +
                    "- 불필요하게 기사 전체를 요약하거나 반복하지 마세요.",
                    "기사 내용:\n" + crawledContent + "\n\n사용자 질문:\n" + question
            );
        }

        processGeminiStreaming(emitter, body, question, userId, articleId);
        return emitter;
    }

    // 단일 질의용 (summarizeContent, 비로그인 askGPT)
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

    // 컨텍스트 기반 질의용 (로그인 유저 askGPT) - 슬라이딩 윈도우 이전 대화 포함
    private Map<String, Object> createContextualRequestBody(String crawledContent, String question,
                                                             List<ChatHistory> history) {
        // 기사 내용은 system_instruction에 포함 (매 turn 반복 전달 방지)
        String systemPrompt = "다음 기사 내용을 주요 참고 자료로 활용하되, 기사에 없는 정보는 일반 지식을 활용해 자세하게 답변해줘.\n\n기사 내용:\n" + crawledContent;

        List<Map<String, Object>> contents = new ArrayList<>();

        // 이전 대화 내역 (user -> model 교대)
        for (ChatHistory chat : history) {
            contents.add(Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", chat.getQuestion()))
            ));
            contents.add(Map.of(
                    "role", "model",
                    "parts", List.of(Map.of("text", chat.getAnswer()))
            ));
        }

        // 현재 질문
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", question))
        ));

        return Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))
                ),
                "contents", contents
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
                        log.warn("[Gemini SSE] JSON 파싱 오류: {}", e.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    // 스트리밍 완료 후 히스토리 저장
                    if (userId != null && articleId != null && question != null) {
                        chatHistoryService.save(userId, articleId, question, resultBuilder.toString());
                    }
                    try {
                        emitter.complete();
                    } catch (Exception e) {
                        log.warn("[Gemini SSE] emitter complete 오류: {}", e.getMessage());
                    }
                })
                .doOnError(error -> {
                    log.error("[Gemini SSE] 처리 중 오류 발생", error);
                    emitter.completeWithError(error);
                })
                .subscribe();
    }


    // GPT (비활성 / 주석 보존)
    // private final WebClient openAiWebClient;
    //
    // public SseEmitter summarizeContent(String crawledContent, String language) { ... }
    // public SseEmitter askGPT(...) { ... }
    // private void processStreamingRequest(...) { ... }
    // private Map<String, Object> createRequestBody(...) { ... }
    // private Map<String, Object> createQuestionRequestBody(...) { ... }
}
