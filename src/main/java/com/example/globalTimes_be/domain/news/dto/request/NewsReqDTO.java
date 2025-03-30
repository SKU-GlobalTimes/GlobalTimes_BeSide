package com.example.globalTimes_be.domain.news.dto.request;

import lombok.Getter;

@Getter
public class NewsReqDTO {
    private String sourceName;
    private String author;
    private String title;
    private String description;
    private String content;
    private String url;
    private String urlToImage;
    private String publishedAt;
}
