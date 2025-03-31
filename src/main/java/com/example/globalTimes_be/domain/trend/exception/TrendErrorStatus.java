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
    _FAIL_SERIALIZATION(HttpStatus.BAD_REQUEST, "직렬화에 실패하였습니다."),
    _FAIL_DESERIALIZATION(HttpStatus.BAD_REQUEST, "역직렬화에 실패하였습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String message;
}