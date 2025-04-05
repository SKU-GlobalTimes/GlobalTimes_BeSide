package com.example.globalTimes_be.domain.test.controller;

import com.example.globalTimes_be.externalApi.service.NewsApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/news")
public class testForFetch {

    private final NewsApiService newsApiService;

    @Autowired
    public testForFetch(NewsApiService newsApiService) {
        this.newsApiService = newsApiService;
    }

    // 테스트 엔드포인트
    @GetMapping("/test")
    public ResponseEntity<String> fetchAndSaveNews() {
        try {
            newsApiService.fetchAndSave();  // 단일 요청 : 100개 까지.
            return ResponseEntity.ok("뉴스 데이터가 성공적으로 저장됨.");
        } catch (Exception e) {
            e.printStackTrace();  // ✅ 콘솔에 상세한 에러 로그 출력
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("문제가 있어! 에러 메시지: " + e.getMessage());  //클라이언트에 에러 메시지 반환
        }
    }
}
