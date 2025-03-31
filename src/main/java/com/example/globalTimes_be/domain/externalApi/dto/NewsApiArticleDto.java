package com.example.globalTimes_be.domain.externalApi.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NewsApiArticleDto {
    // 개별 뉴스 기사의 데이터를 위한 Dto 설정

    private String author;         // 저자
    private String title;          // 제목
    private String description;    // 본문 요약
    private String content;        // 본문 내용
    private String url;            // 기사 URL
    private String urlToImage;     // 썸네일 URL
    private String publishedAt;    // 발행 일자
    private NewsApiSourceDto source;  // 언론사 정보

}
