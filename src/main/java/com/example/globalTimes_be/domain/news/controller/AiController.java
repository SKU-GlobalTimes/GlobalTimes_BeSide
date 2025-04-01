package com.example.globalTimes_be.domain.news.controller;

import com.example.globalTimes_be.domain.news.exception.NewsErrorStatus;
import com.example.globalTimes_be.domain.news.service.AiService;
import com.example.globalTimes_be.domain.news.service.AiSseService;
import com.example.globalTimes_be.domain.news.service.NewsService;
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
    public final NewsService newsService;

    //기사의 id와 사용자 언어를 받으면 원본 기사의 url로 들어가서 p태그 크롤링 후 gpt를 통해 요약
    // 한번에 기사 요약 반환
    @Override
    @GetMapping(value = "/{id}/summary")
    public ResponseEntity<ApiResponse> summarizeArticle(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "영어") String language){
        //해당 기사 내용을 가져옴
        String crawledContent = newsService.getArticleCrawledContent(id);

        //크롤링한 기사 내용이 없으면 본문 서두 반환
        if (crawledContent == null) {
            //기사 id에 맞는 content 가져옴
            String content = newsService.getArticleContent(id);

            return ApiResponse.fail(NewsErrorStatus._CRAWLER_ERROR.getResponse(), content);
        }

        //해당 기사를 요약함
        String summary = aiService.summarizeArticle(crawledContent, language);

        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), summary);
    }

    //sse로 기사 요약 반환
    @Override
    @GetMapping(value = "/{id}/summary/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter summarizeArticleSse(@PathVariable Long id,
                                       @RequestParam(defaultValue = "영어") String language){
        //해당 기사 내용을 가져옴
        String crawledContent = newsService.getArticleCrawledContent(id);

        //크롤링한 기사 내용이 없으면 에러 처리
        if (crawledContent == null) {
            throw new BaseException(NewsErrorStatus._CRAWLER_ERROR.getResponse());
        }

        //GPT 요약 요청 후 SSE 스트리밍 전송
        return  aiSseService.summarizeContent(crawledContent, language);
    }

    //프론트에게 DB에 저장된 기사를 바탕으로 사용자에게 답변 제공
    @Override
    @GetMapping(value = "/{id}/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askArticle(@PathVariable Long id,
                                 @RequestParam String question){
        //해당 기사 내용을 가져옴
        String crawledContent = newsService.getArticleCrawledContent(id);

        //크롤링한 기사 내용이 없으면 에러 처리
        if (crawledContent == null) {
            throw new BaseException(NewsErrorStatus._CRAWLER_ERROR.getResponse());
        }
        //GPT 답변 요청 후 SSE 스트리밍 전송
        return aiSseService.askGPT(crawledContent, question);
    }
}
