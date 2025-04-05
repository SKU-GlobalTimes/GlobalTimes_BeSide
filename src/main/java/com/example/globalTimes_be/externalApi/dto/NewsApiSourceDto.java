package com.example.globalTimes_be.externalApi.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NewsApiSourceDto {

    //언론사 정보 Dto
    private String id;       // (nullable)
    private String name;
}
