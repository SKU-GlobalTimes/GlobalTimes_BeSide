package com.example.globalTimes_be.domain.search.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "검색 결과 페이지 응답 DTO")
public class SearchResDTO {
    @Schema(description = "번역 전 문자열")
    private String originalText;

    @Schema(description = "번역 후 문자열")
    private String translatedText;

    @Schema(description = "검색 결과에 대한 뉴스 데이터", implementation = SearchArticleDTO.class)
    private List<SearchArticleDTO> searchArticles;

}
