package com.example.globalTimes_be.global.apiPayload.code.status;

import com.example.globalTimes_be.global.apiPayload.code.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GlobalSuccessStatus implements BaseResponse {
    _OK(HttpStatus.OK, "응답에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
