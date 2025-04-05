package com.example.globalTimes_be.domain.scrap.exception;

import com.example.globalTimes_be.global.apiPayload.code.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ScrapErrorStatus implements BaseResponse {
    _CUSTOM_ERROR(HttpStatus.BAD_REQUEST, "에러테스트 요청입니다."),

    _EMPTY_SCRAP_ARTICLE(HttpStatus.INTERNAL_SERVER_ERROR, "요청한 기사에 대한 정보가 없습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String message;
}