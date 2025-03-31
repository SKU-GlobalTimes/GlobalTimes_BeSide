package com.example.globalTimes_be.global.apiPayload.code;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Builder
public record ApiResponse(
        @Schema(description = "API 호출 일시", example = "2024-10-30T15:38:12.43483271")
        String timestamp,

        @Schema(description = "API 호출 성공 여부", example = "true")
        boolean isSuccess,

        @Schema(description = "응답 메시지", example = "호출에 성공하였습니다.")
        String message,

        @Schema(description = "응답 데이터", example = "null")
        Object data) {

    public ApiResponse {
        timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
    }

    public static ResponseEntity<ApiResponse> success(ResponseDTO responseDTO, Object data) {
        return ResponseEntity
                .status(responseDTO.httpStatus())
                .body(
                        ApiResponse.builder()
                                .isSuccess(true)
                                .message(responseDTO.message())
                                .data(data)
                                .build()
                );
    }

    public static ResponseEntity<ApiResponse> success(ResponseDTO responseDTO) {
        return ResponseEntity
                .status(responseDTO.httpStatus())
                .body(
                        ApiResponse.builder()
                                .isSuccess(true)
                                .message(responseDTO.message())
                                .data(null)
                                .build()
                );
    }

    public static ResponseEntity<ApiResponse> fail(ResponseDTO responseDTO, Object data) {
        return ResponseEntity
                .status(responseDTO.httpStatus())
                .body(
                        ApiResponse.builder()
                                .isSuccess(false)
                                .message(responseDTO.message())
                                .data(data)
                                .build()
                );
    }

    public static ResponseEntity<ApiResponse> fail(ResponseDTO responseDTO) {
        return ResponseEntity
                .status(responseDTO.httpStatus())
                .body(
                        ApiResponse.builder()
                                .isSuccess(false)
                                .message(responseDTO.message())
                                .data(null)
                                .build()
                );
    }

    public static ResponseEntity<ApiResponse> fail(String message, HttpStatus httpStatus) {
        return ResponseEntity
                .status(httpStatus)
                .body(
                        ApiResponse.builder()
                                .isSuccess(false)
                                .message(message)
                                .data(null)
                                .build()
                );
    }
}