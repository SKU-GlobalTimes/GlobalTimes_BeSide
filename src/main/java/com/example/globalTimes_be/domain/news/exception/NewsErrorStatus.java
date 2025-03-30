package com.example.globalTimes_be.domain.news.exception;

import com.example.globalTimes_be.global.apiPayload.code.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum NewsErrorStatus implements BaseResponse {
    _CUSTOM_ERROR(HttpStatus.BAD_REQUEST, "에러테스트 요청입니다."),

    _EMPTY_NEWS_DATA(HttpStatus.BAD_REQUEST, "해당 기사의 정보가 없습니다,"),

    _CRAWLER_ERROR(HttpStatus.BAD_REQUEST, "해당 언론사는 요약 정보 제공이 불가능합니다."),
    ;

    private final HttpStatus httpStatus;
    private final String message;
}