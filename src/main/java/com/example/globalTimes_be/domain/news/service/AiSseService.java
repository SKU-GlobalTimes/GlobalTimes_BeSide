package com.example.globalTimes_be.domain.news.service;

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

    // GPT 응답을 스트리밍(SSE)으로 처리
    public SseEmitter summarizeContent(String crawledContent, String language) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L); // 10분 타임아웃 설정
        processStreamingRequest(emitter, createRequestBody(crawledContent, language));
        return emitter;
    }

    // 사용자 질문에 대해 GPT 답변을 SSE로 전송
    public SseEmitter askGPT(String crawledContent, String question){
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L); // 10분 타임아웃 설정
        processStreamingRequest(emitter, createQuestionRequestBody(crawledContent, question));
        return emitter;
    }

    private void processStreamingRequest(SseEmitter emitter, Map<String, Object> requestBody) {
        StringBuilder resultBuilder = new StringBuilder(); // 🔹 누적용 StringBuilder

        openAiWebClient.post()
                .uri("/chat/completions")
                .accept(MediaType.TEXT_EVENT_STREAM) // 🔹 SSE 형식 요청
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class) // 🔹 OpenAI 응답 JSON 처리
                .doOnNext(data -> {
                    String jsonPart = data.trim();

                    // DONE 처리
                    if ("[DONE]".equals(jsonPart)) {
                        emitter.complete();
                        return;
                    }

                    try {
                        Map response = new ObjectMapper().readValue(jsonPart, Map.class);

                        // delta -> content 가져오기
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
                        log.warn("JSON 파싱 오류 발생!", e); //
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
                "stream", true // 스트리밍 활성화
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
