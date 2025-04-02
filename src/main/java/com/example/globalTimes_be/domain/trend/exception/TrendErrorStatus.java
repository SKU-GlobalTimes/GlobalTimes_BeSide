package com.example.globalTimes_be.domain.trend.exception;

import com.example.globalTimes_be.global.apiPayload.code.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TrendErrorStatus implements BaseResponse {
    _CUSTOM_ERROR(HttpStatus.BAD_REQUEST, "에러테스트 요청입니다."),

    _INVALID_COUNTRY_CODE(HttpStatus.BAD_REQUEST, "잘못된 국가 코드입니다."),
    _FAIL_SERIALIZATION(HttpStatus.INTERNAL_SERVER_ERROR, "직렬화에 실패하였습니다."),
    _FAIL_DESERIALIZATION(HttpStatus.INTERNAL_SERVER_ERROR, "역직렬화에 실패하였습니다."),

    _CRAWLER_ERROR(HttpStatus.BAD_REQUEST, "해당 언론사는 요약 정보 제공이 불가능합니다. (크롤링 불가)"),

    _GPT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GPT 요약 중 에러가 발생하였습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String message;
}