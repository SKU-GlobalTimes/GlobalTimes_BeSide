package com.example.globalTimes_be.domain.trend.controller;

import com.example.globalTimes_be.domain.trend.dto.resonse.TrendDTO;
import com.example.globalTimes_be.domain.trend.service.TrendService;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/trend")
public class TrendController implements TrendControllerDocs {
    private final TrendService trendService;

    //나라별 실검 정보 가져옴
    @Override
    @GetMapping
    public ResponseEntity<ApiResponse> getTrend(@RequestParam String code){
        List<TrendDTO> trendDTOS = trendService.getTrendingKeywords(code);

        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), trendDTOS);
    }
}