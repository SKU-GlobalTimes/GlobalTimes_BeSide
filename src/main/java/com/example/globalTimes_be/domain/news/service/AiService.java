package com.example.globalTimes_be.domain.news.service;

import com.example.globalTimes_be.domain.news.exception.NewsErrorStatus;
import com.example.globalTimes_be.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class AiService {
    private final WebClient openAiWebClient;

    public String summarizeArticle(String crawledContent, String language){
        String summary = openAiWebClient.post()
                .uri("/chat/completions")
                .bodyValue(createRequestBody(crawledContent, language))  // 요청 본문
                .retrieve()
                .bodyToMono(Map.class)  // 전체 응답을 한 번에 받음
                .map(response -> extractContent(response))  // 응답 본문에서 요약 내용 추출
                .block();

        return summary;
    }

    // OpenAI 요청 본문 생성 (기사 요약)
    private Map<String, Object> createRequestBody(String crawledContent, String language) {
        return Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "system", "content", "이 기사를 " + language + "로 요약해줘."),
                        Map.of("role", "user", "content", crawledContent)
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
        throw new BaseException(NewsErrorStatus._GPT_ERROR.getResponse());
    }
}
