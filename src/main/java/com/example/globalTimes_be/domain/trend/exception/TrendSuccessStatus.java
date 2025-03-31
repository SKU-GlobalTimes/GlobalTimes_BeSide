package com.example.globalTimes_be.domain.trend.exception;

import com.example.globalTimes_be.global.apiPayload.code.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TrendSuccessStatus implements BaseResponse {
    _SUCCESS_TEST(HttpStatus.OK, "성공 테스트 확인"),
    ;

    private final HttpStatus httpStatus;
    private final String message;
}
