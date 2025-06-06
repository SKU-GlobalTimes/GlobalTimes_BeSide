package com.example.globalTimes_be.externalApi.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class NewsApiResponseDto {
    // 응답받은 전체 데이터 Dto ( 최상위 )

    private String status;
    private int totalResults;
    private List<NewsApiArticleDto> articles;
}
