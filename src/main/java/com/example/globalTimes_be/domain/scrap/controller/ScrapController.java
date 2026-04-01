package com.example.globalTimes_be.domain.scrap.controller;

import com.example.globalTimes_be.domain.scrap.dto.response.ScrapResDTO;
import com.example.globalTimes_be.domain.scrap.service.ScrapService;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ScrapController implements ScrapControllerDocs {

    private final ScrapService scrapService;

    // 기존 API 유지 (localStorage 기반 비로그인 호환)
    @Override
    @GetMapping("/scrap")
    public ResponseEntity<ApiResponse> getScrapArticle(@RequestParam List<Long> id) {
        List<ScrapResDTO> scrapResDTOs = scrapService.getScrap(id);
        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), scrapResDTOs);
    }

    // 스크랩 토글 (로그인 필요)
    @Override
    @PostMapping("/articles/{id}/scrap")
    public ResponseEntity<ApiResponse> toggleScrap(
            @PathVariable("id") Long articleId,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        boolean scrapped = scrapService.toggle(userId, articleId);
        return ApiResponse.success(
                GlobalSuccessStatus._OK.getResponse(),
                Map.of("scrapped", scrapped)
        );
    }

    // 내 스크랩 목록 (로그인 필요)
    @Override
    @GetMapping("/user/scraps")
    public ResponseEntity<ApiResponse> getMyScrapList(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(
                GlobalSuccessStatus._OK.getResponse(),
                scrapService.getScrapList(userId)
        );
    }

    // 특정 기사 스크랩 여부 (로그인 필요)
    @Override
    @GetMapping("/articles/{id}/scrap/status")
    public ResponseEntity<ApiResponse> getScrapStatus(
            @PathVariable("id") Long articleId,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        boolean scrapped = scrapService.isScrapped(userId, articleId);
        return ApiResponse.success(
                GlobalSuccessStatus._OK.getResponse(),
                Map.of("scrapped", scrapped)
        );
    }
}
