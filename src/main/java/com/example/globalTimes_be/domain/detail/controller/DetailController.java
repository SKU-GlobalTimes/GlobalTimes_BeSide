package com.example.globalTimes_be.domain.detail.controller;

import com.example.globalTimes_be.domain.detail.dto.response.DetailResDTO;
import com.example.globalTimes_be.domain.detail.service.DetailService;
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

    @Override
    @GetMapping("/detail")
    public ResponseEntity<ApiResponse> getNewsDetail(@RequestParam("id") Long id) {
        DetailResDTO detailResDTO = detailService.getNewsDetail(id);
        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), detailResDTO);
    }
}
