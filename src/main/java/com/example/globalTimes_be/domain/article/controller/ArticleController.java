package com.example.globalTimes_be.domain.article.controller;

import com.example.globalTimes_be.domain.article.ApiDoc.ArticleApiDocumentation;
import com.example.globalTimes_be.domain.article.service.ArticleService;
import com.example.globalTimes_be.domain.article.dto.ArticleResponseDto;
import com.example.globalTimes_be.domain.article.dto.CursorArticleResponseDto;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController implements ArticleApiDocumentation {

    private final ArticleService articleService;

    // 최신순
    @Override
    @GetMapping("/latest")
    public ResponseEntity<?> getArticlesByPublishedAt(
            @RequestParam(name = "page") int page,
            @RequestParam(name = "size") int size) {

        Page<ArticleResponseDto> articles = articleService.getArticlesByPublishedAt(page, size);

        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), articles);
    }

    // Cursor 기반 최신순 (기존 Offset API와 병행 제공, FE 연동 후 Offset 제거 예정)
    @GetMapping("/cursor")
    public ResponseEntity<?> getArticlesByCursor(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {

        CursorArticleResponseDto response = articleService.getArticlesByCursor(cursor, size);

        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), response);
    }

    // 조회수 높은 순
    @Override
    @GetMapping("/popular")
    public ResponseEntity<?> getArticlesByViewCount(
            @RequestParam(name = "page") int page, // page와 size는 필수로 클라이언트가 제공
            @RequestParam(name = "size") int size) {

        Page<ArticleResponseDto> articles = articleService.getArticlesByViewCount(page, size);

        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), articles);
    }
}
