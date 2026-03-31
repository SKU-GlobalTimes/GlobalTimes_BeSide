package com.example.globalTimes_be.domain.ai.controller;

import com.example.globalTimes_be.domain.ai.service.AiService;
import com.example.globalTimes_be.domain.ai.service.AiSseService;
import com.example.globalTimes_be.domain.detail.exception.DetailErrorStatus;
import com.example.globalTimes_be.domain.detail.exception.DetailSuccessStatus;
import com.example.globalTimes_be.domain.detail.service.DetailService;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalSuccessStatus;
import com.example.globalTimes_be.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/ai")
public class AiController implements AiControllerDocs {
    public final AiService aiService;
    public final AiSseService aiSseService;
    public final DetailService detailService;

    @Override
    @GetMapping(value = "/{id}/summary")
    public ResponseEntity<ApiResponse> summarizeArticle(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "영어") String language) {
        // DB에 저장된 요약이 있으면 GPT 스킵
        String saved = detailService.getArticleSummary(id, language);
        if (saved != null && !saved.isBlank()) {
            return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), saved);
        }

        String crawledContent = detailService.getArticleCrawledContent(id);

        if (crawledContent == null) {
            String content = detailService.getArticleContent(id);
            return ApiResponse.fail(DetailSuccessStatus._CRAWLER_FAIL.getResponse(), content);
        }

        String summary = aiService.summarizeArticle(crawledContent, language);

        // 요약 결과 DB 저장 (이후 재요청 시 GPT 스킵)
        detailService.saveArticleSummary(id, summary);

        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), summary);
    }

    @Override
    @GetMapping(value = "/{id}/summary/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter summarizeArticleSse(@PathVariable Long id,
                                          @RequestParam(defaultValue = "영어") String language) {
        String crawledContent = detailService.getArticleCrawledContent(id);

        if (crawledContent == null) {
            throw new BaseException(DetailErrorStatus._CRAWLER_ERROR.getResponse());
        }

        return aiSseService.summarizeContent(crawledContent, language);
    }

    @Override
    @GetMapping(value = "/{id}/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askArticle(@PathVariable Long id,
                                 @RequestParam String question,
                                 Authentication authentication) {
        String crawledContent = detailService.getArticleCrawledContent(id);

        if (crawledContent == null) {
            throw new BaseException(DetailErrorStatus._CRAWLER_ERROR.getResponse());
        }

        // 로그인 사용자면 userId 전달 → 스트리밍 완료 후 히스토리 저장
        // 비로그인이면 null 전달 → 저장 생략 (기존 동작 유지)
        Long userId = (authentication != null) ? (Long) authentication.getPrincipal() : null;
        return aiSseService.askGPT(crawledContent, question, userId, id);
    }
}
