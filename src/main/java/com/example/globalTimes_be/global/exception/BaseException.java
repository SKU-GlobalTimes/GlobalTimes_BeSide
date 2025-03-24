package com.example.globalTimes_be.global.exception;

import com.example.globalTimes_be.global.apiPayload.code.ResponseDTO;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BaseException extends RuntimeException {
    private final HttpStatus httpStatus;

    public BaseException(ResponseDTO responseDTO) {
        super(responseDTO.message());
        this.httpStatus = responseDTO.httpStatus();
    }
}

