package com.example.globalTimes_be.domain.article.controller;

import com.example.globalTimes_be.domain.article.dto.CursorArticleResponseDto;
import com.example.globalTimes_be.domain.article.service.ExploreService;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/articles")
public class ExploreController implements ExploreControllerDocs {

    private final ExploreService exploreService;

    @Override
    @GetMapping("/explore")
    public ResponseEntity<ApiResponse> explore(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        CursorArticleResponseDto response = exploreService.explore(country, category, date, cursor, size);
        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), response);
    }
}
