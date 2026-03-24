package com.example.globalTimes_be.domain.detail.controller;

import com.example.globalTimes_be.domain.detail.dto.response.DetailResDTO;
import com.example.globalTimes_be.domain.detail.dto.response.PerspectivesResDTO;
import com.example.globalTimes_be.domain.detail.service.DetailService;
import com.example.globalTimes_be.domain.detail.service.PerspectivesService;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/news")
public class DetailController implements DetailControllerDocs {
    private final DetailService detailService;
    private final PerspectivesService perspectivesService;

    @Override
    @GetMapping("/detail")
    public ResponseEntity<ApiResponse> getNewsDetail(@RequestParam("id") Long id) {
        DetailResDTO detailResDTO = detailService.getNewsDetail(id);
        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), detailResDTO);
    }

    // 기사 상세에서 타 국가 관련 기사 비교 (국가별 시각 비교)
    @GetMapping("/{id}/perspectives")
    public ResponseEntity<ApiResponse> getPerspectives(@PathVariable Long id) {
        PerspectivesResDTO response = perspectivesService.getPerspectives(id);
        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), response);
    }
}
