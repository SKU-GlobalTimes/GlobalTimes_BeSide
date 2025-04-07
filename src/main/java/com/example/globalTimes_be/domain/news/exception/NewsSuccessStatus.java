package com.example.globalTimes_be.domain.news.exception;

import com.example.globalTimes_be.global.apiPayload.code.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum NewsSuccessStatus implements BaseResponse {
    _SUCCESS_TEST(HttpStatus.OK, "성공 테스트 확인"),

    _CRAWLER_FAIL(HttpStatus.ACCEPTED, "요약정보 제공 불가 (크롤링 에러, 기사 서두 제공)"),
    ;

    private final HttpStatus httpStatus;
    private final String message;
}
