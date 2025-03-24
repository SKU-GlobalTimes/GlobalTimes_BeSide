package com.example.globalTimes_be.global.apiPayload.code.status;

import com.example.globalTimes_be.global.apiPayload.code.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GlobalErrorStatus implements BaseResponse {
    _BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    _UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "사용자 인증이 필요합니다."),
    _FORBIDDEN(HttpStatus.FORBIDDEN, "해당 요청에 접근 권한이 없습니다."),
    _NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 리소스입니다."),
    _CONFLICT(HttpStatus.CONFLICT,"잘못된 입력입니다."),
    _INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 에러가 발생하였습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
