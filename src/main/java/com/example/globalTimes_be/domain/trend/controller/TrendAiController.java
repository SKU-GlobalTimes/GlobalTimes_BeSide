package com.example.globalTimes_be.domain.trend.controller;

import com.example.globalTimes_be.domain.trend.exception.TrendSuccessStatus;
import com.example.globalTimes_be.domain.trend.service.TrendAiService;
import com.example.globalTimes_be.domain.trend.service.TrendCrawledService;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/trend")
public class TrendAiController implements TrendAiControllerDocs{
    private final TrendCrawledService trendCrawledService;
    private final TrendAiService trendAiService;

    //기사의 url을 받으면 요약해주는 api 제공
    @Override
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse> getSummarizeTrendArticle (@RequestParam String url,
                                                                 @RequestParam(defaultValue = "영어") String language) {
        //본문 크롤링
        String content = trendCrawledService.getArticleCrawledContent(url);
        //크롤링한 본문 요약
        String summary = trendAiService.summarizeTrendArticle(content, language);
        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), summary);
    }
}
