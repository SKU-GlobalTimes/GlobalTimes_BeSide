package com.example.globalTimes_be.domain.news.controller.test;

import com.example.globalTimes_be.domain.news.service.NewsService;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/test/news")
public class NewsTestController {
    private final NewsService newsService;
//    private final NewsRepository newsRepository;
//
//    //테스트용!!!!!
//    @PostMapping("/save")
//    public ResponseEntity<ApiResponse> saveNews(@RequestBody NewsReqDTO newsReqDTO) {
//        NewsEntity newsEntity = NewsEntity.builder()
//                .sourceName(newsReqDTO.getSourceName())
//                .author(newsReqDTO.getAuthor())
//                .title(newsReqDTO.getTitle())
//                .description(newsReqDTO.getDescription())
//                .content(newsReqDTO.getContent())
//                .url(newsReqDTO.getUrl())
//                .urlToImage(newsReqDTO.getUrlToImage())
//                .publishedAt(newsReqDTO.getPublishedAt())
//                .build();
//
//        newsRepository.save(newsEntity);
//        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse());
//    }

    //테스트용!!!!!!
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse> getNewsContent(@RequestParam("id") Long id) {
        String crawled_content = newsService.getArticleCrawledContent(id);

        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), crawled_content);
    }
}
