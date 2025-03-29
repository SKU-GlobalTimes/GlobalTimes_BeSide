package com.example.globalTimes_be.externalApi;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class NewsApiResponseDto {
    // 응답받은 전체 데이터 Dto ( 최상위 )

    private String status;
    private int totalResult;
    private List<NewsApiArticleDto> articles;
}
