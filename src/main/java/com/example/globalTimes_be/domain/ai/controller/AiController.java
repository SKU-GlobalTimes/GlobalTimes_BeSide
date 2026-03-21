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
        String crawledContent = detailService.getArticleCrawledContent(id);

        if (crawledContent == null) {
            String content = detailService.getArticleContent(id);
            return ApiResponse.fail(DetailSuccessStatus._CRAWLER_FAIL.getResponse(), content);
        }

        String summary = aiService.summarizeArticle(crawledContent, language);
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
                                 @RequestParam String question) {
        String crawledContent = detailService.getArticleCrawledContent(id);

        if (crawledContent == null) {
            throw new BaseException(DetailErrorStatus._CRAWLER_ERROR.getResponse());
        }

        return aiSseService.askGPT(crawledContent, question);
    }
}
