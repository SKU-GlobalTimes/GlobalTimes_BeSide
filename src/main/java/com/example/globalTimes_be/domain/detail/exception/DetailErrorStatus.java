package com.example.globalTimes_be.domain.detail.exception;

import com.example.globalTimes_be.global.apiPayload.code.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum DetailErrorStatus implements BaseResponse {
    _CUSTOM_ERROR(HttpStatus.BAD_REQUEST, "에러테스트 요청입니다."),

    _EMPTY_NEWS_DATA(HttpStatus.BAD_REQUEST, "해당 기사의 정보가 없습니다,"),

    _CRAWLER_ERROR(HttpStatus.BAD_REQUEST, "해당 언론사는 요약 정보 제공이 불가능합니다. (크롤링 불가)"),

    _GEMINI_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "외부 AI 요약 서비스 응답에 실패하였습니다."),

    _GEMINI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "외부 AI 요약 서비스 응답 시간이 초과되었습니다."),

    _GPT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GPT 요약 중 에러가 발생하였습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String message;
}
