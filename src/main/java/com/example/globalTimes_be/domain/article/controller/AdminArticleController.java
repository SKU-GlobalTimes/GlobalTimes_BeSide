package com.example.globalTimes_be.domain.article.controller;

import com.example.globalTimes_be.domain.article.dto.AdminArticleSearchDto;
import com.example.globalTimes_be.domain.article.service.AdminArticleService;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalSuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "Admin", description = "관리자용 데이터 조회 API")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminArticleController {

    private final AdminArticleService adminArticleService;

    @Operation(summary = "기사 필터 조회",
            description = "country / category / 기간(from~to) 필터와 페이징으로 기사를 조회합니다. " +
                    "모든 파라미터는 선택값이며, 생략 시 전체 조회합니다.")
    @GetMapping("/articles")
    public ResponseEntity<?> searchArticles(
            @Parameter(description = "국가 코드 (예: us, gb, global)")
            @RequestParam(required = false) String country,

            @Parameter(description = "카테고리 (예: general, technology, sports)")
            @RequestParam(required = false) String category,

            @Parameter(description = "조회 시작 일시 (예: 2024-01-01T00:00:00)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,

            @Parameter(description = "조회 종료 일시 (예: 2024-12-31T23:59:59)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AdminArticleSearchDto> result =
                adminArticleService.searchArticles(country, category, from, to, page, size);

        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), result);
    }
}
