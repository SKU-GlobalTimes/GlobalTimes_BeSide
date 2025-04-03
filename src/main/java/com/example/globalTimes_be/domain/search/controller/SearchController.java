package com.example.globalTimes_be.domain.search.controller;

import com.example.globalTimes_be.domain.search.dto.response.SearchResDTO;
import com.example.globalTimes_be.domain.search.service.SearchArticlesService;
import com.example.globalTimes_be.domain.search.service.TranslationService;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/search")
public class SearchController implements SearchControllerDocs {
    private final TranslationService translationService;
    private final SearchArticlesService searchArticlesService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse> getSearchArticles(@RequestParam String text){
        //검색어를 영어로 번역
        String translatedText = translationService.translateToEnglish(text);

        //원본 검색어 + 번역된 검색어로 기사 검색
        SearchResDTO searchResDTO = searchArticlesService.getSearchArticles(text, translatedText);

        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), searchResDTO);
    }
}
