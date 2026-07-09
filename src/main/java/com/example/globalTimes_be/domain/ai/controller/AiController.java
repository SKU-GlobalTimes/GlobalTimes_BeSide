package com.example.globalTimes_be.domain.ai.controller;

import com.example.globalTimes_be.domain.ai.service.AiService;
import com.example.globalTimes_be.domain.ai.service.AiSseService;
import com.example.globalTimes_be.domain.chat.service.AnonymousChatSessionService;
import com.example.globalTimes_be.domain.detail.exception.DetailErrorStatus;
import com.example.globalTimes_be.domain.detail.exception.DetailSuccessStatus;
import com.example.globalTimes_be.domain.detail.service.DetailService;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalSuccessStatus;
import com.example.globalTimes_be.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;

@RequiredArgsConstructor
@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiController implements AiControllerDocs {
    public final AiService aiService;
    public final AiSseService aiSseService;
    public final DetailService detailService;
    public final AnonymousChatSessionService anonymousChatSessionService;

    @Override
    @GetMapping(value = "/{id}/summary")
    public ResponseEntity<ApiResponse> summarizeArticle(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "영어") String language) {
        long startedAt = System.nanoTime();

        // DB에 저장된 요약이 있으면 GPT 스킵
        long summaryLookupStartedAt = System.nanoTime();
        String saved = detailService.getArticleSummary(id, language);
        long summaryLookupMs = elapsedMs(summaryLookupStartedAt);
        if (saved != null && !saved.isBlank()) {
            log.info("[AiSummary] articleId={} summaryHit=true crawledContentRequested=false crawlerFallback=false aiRequested=false summaryLookupMs={} crawlContentMs=0 fallbackContentMs=0 aiSummaryMs=0 summarySaveMs=0 totalMs={}",
                    id,
                    summaryLookupMs,
                    elapsedMs(startedAt));
            return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), saved);
        }

        long crawlContentStartedAt = System.nanoTime();
        String crawledContent = detailService.getArticleCrawledContent(id);
        long crawlContentMs = elapsedMs(crawlContentStartedAt);

        if (crawledContent == null) {
            long fallbackContentStartedAt = System.nanoTime();
            String content = detailService.getArticleContent(id);
            long fallbackContentMs = elapsedMs(fallbackContentStartedAt);
            log.info("[AiSummary] articleId={} summaryHit=false crawledContentRequested=true crawlerFallback=true aiRequested=false summaryLookupMs={} crawlContentMs={} fallbackContentMs={} aiSummaryMs=0 summarySaveMs=0 totalMs={}",
                    id,
                    summaryLookupMs,
                    crawlContentMs,
                    fallbackContentMs,
                    elapsedMs(startedAt));
            return ApiResponse.fail(DetailSuccessStatus._CRAWLER_FAIL.getResponse(), content);
        }

        long aiSummaryStartedAt = System.nanoTime();
        String summary = aiService.summarizeArticle(crawledContent, language);
        long aiSummaryMs = elapsedMs(aiSummaryStartedAt);

        // 요약 결과 DB 저장 (이후 재요청 시 GPT 스킵)
        long summarySaveStartedAt = System.nanoTime();
        detailService.saveArticleSummary(id, summary);
        long summarySaveMs = elapsedMs(summarySaveStartedAt);

        log.info("[AiSummary] articleId={} summaryHit=false crawledContentRequested=true crawlerFallback=false aiRequested=true summaryLookupMs={} crawlContentMs={} fallbackContentMs=0 aiSummaryMs={} summarySaveMs={} totalMs={}",
                id,
                summaryLookupMs,
                crawlContentMs,
                aiSummaryMs,
                summarySaveMs,
                elapsedMs(startedAt));

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
                                 @RequestHeader(value = "X-Anonymous-Session", required = false) String anonymousSessionHeader,
                                 @RequestParam(value = "anonymousSession", required = false) String anonymousSessionQuery,
                                 Authentication authentication) {
        String crawledContent = detailService.getArticleCrawledContent(id);

        if (crawledContent == null) {
            throw new BaseException(DetailErrorStatus._CRAWLER_ERROR.getResponse());
        }

        Long userId = (authentication != null) ? (Long) authentication.getPrincipal() : null;
        // 로그인 시 DB 히스토리만 사용 (익명 식별자 무시)
        String anonymousSessionId = (userId != null) ? null
                : (anonymousSessionHeader != null && !anonymousSessionHeader.isBlank()
                ? anonymousSessionHeader
                : anonymousSessionQuery);
        return aiSseService.askGPT(crawledContent, question, userId, id, anonymousSessionId);
    }

    /**
     * 비로그인 전용: Redis에 저장된 해당 기사 대화 목록 (오래된 순). 유효한 X-Anonymous-Session 필요.
     */
    @Override
    @GetMapping("/{id}/ask/history")
    public ResponseEntity<ApiResponse> getAnonymousArticleChatHistory(
            @PathVariable Long id,
            @RequestHeader(value = "X-Anonymous-Session", required = false) String anonymousSessionHeader,
            @RequestParam(value = "anonymousSession", required = false) String anonymousSessionQuery) {
        String anonymousSessionId = (anonymousSessionHeader != null && !anonymousSessionHeader.isBlank())
                ? anonymousSessionHeader
                : anonymousSessionQuery;
        if (!AnonymousChatSessionService.isValidSessionId(anonymousSessionId)) {
            return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), Collections.emptyList());
        }
        return ApiResponse.success(
                GlobalSuccessStatus._OK.getResponse(),
                anonymousChatSessionService.getFullHistory(anonymousSessionId.trim(), id)
        );
    }

    /**
     * 비로그인: 세션별 대화한 기사 목록 (플로팅 히스토리). 유효한 세션 ID 없으면 빈 배열.
     */
    @Override
    @GetMapping("/anonymous/chat-history")
    public ResponseEntity<ApiResponse> getAnonymousChatHistoryList(
            @RequestHeader(value = "X-Anonymous-Session", required = false) String anonymousSessionHeader,
            @RequestParam(value = "anonymousSession", required = false) String anonymousSessionQuery) {
        String anonymousSessionId = (anonymousSessionHeader != null && !anonymousSessionHeader.isBlank())
                ? anonymousSessionHeader
                : anonymousSessionQuery;
        if (!AnonymousChatSessionService.isValidSessionId(anonymousSessionId)) {
            return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), Collections.emptyList());
        }
        return ApiResponse.success(
                GlobalSuccessStatus._OK.getResponse(),
                anonymousChatSessionService.getAnonymousChatHistoryList(anonymousSessionId.trim())
        );
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
