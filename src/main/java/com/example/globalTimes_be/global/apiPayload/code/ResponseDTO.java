package com.example.globalTimes_be.global.apiPayload.code;

import lombok.Builder;
import org.springframework.http.HttpStatus;

@Builder
public record ResponseDTO(
        HttpStatus httpStatus,
        String message) {
}