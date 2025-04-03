package com.example.globalTimes_be.domain.scrap.controller;

import com.example.globalTimes_be.domain.scrap.dto.response.ScrapResDTO;
import com.example.globalTimes_be.domain.scrap.service.ScrapService;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/scrap")
public class ScrapController implements ScrapControllerDocs{
    private final ScrapService scrapService;

    //id값을 리스트로 받으면 리스트 형식으로 데이터 반환
    @Override
    @GetMapping
    public ResponseEntity<ApiResponse> getScrapArticle(@RequestParam List<Long> id) {
        //서비스단에서 리스트형식 받으면 리스트 형식으로 article 데이터 반환
        List<ScrapResDTO> scrapResDTOs = scrapService.getScrap(id);

        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), scrapResDTOs);
    }
}
