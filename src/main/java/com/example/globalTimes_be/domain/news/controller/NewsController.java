package com.example.globalTimes_be.domain.news.controller;

import com.example.globalTimes_be.domain.news.dto.request.NewsReqDTO;
import com.example.globalTimes_be.domain.news.dto.response.NewsResDTO;
import com.example.globalTimes_be.domain.news.entity.NewsEntity;
import com.example.globalTimes_be.domain.news.repository.NewsRepository;
import com.example.globalTimes_be.domain.news.service.NewsService;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/news")
public class NewsController implements NewsControllerDocs {
    private final NewsService newsService;

    //기사의 id를 받으면 기사 제목, 사진 url, 원본 기사 url, 최근 기사 정보 반환
    @Override
    @GetMapping("/detail")
    public ResponseEntity<ApiResponse> getNewsDetail(@RequestParam("id") Long id) {
        NewsResDTO newsResDTO = newsService.getNewsDetail(id);
        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), newsResDTO);
    }
}
