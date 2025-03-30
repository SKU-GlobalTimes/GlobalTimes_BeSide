package com.example.globalTimes_be.global.exception;

import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalErrorStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalErrorHandler {

    //400 BAD_REQUEST 비즈니스 로직 에러
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse> handleIllegalArgumentException(IllegalArgumentException e){
        log.warn("IllegalArgumentException Error", e);
        return ApiResponse.fail(GlobalErrorStatus._BAD_REQUEST.getResponse());
    }

    //validation 에러
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidationExceptions(MethodArgumentNotValidException e){
        log.warn("MethodArgumentNotValidException Error", e);
        // 유효성 검사 오류 메시지 가져오기
        List<String> errorMessages = e.getBindingResult()
                .getAllErrors()
                .stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.toList());

        // 에러 메시지가 없으면 기본 메시지 반환
        String message = errorMessages.isEmpty() ? "유효성 검사 실패" : String.join(", ", errorMessages);

        // ApiResponse로 반환
        return ApiResponse.fail(message, HttpStatus.BAD_REQUEST);
    }

    // 401 Unauthorized 인증 실패 에러
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse> handleAccessDeniedException(SecurityException e) {
        log.warn("SecurityException Error", e);
        return ApiResponse.fail(GlobalErrorStatus._UNAUTHORIZED.getResponse());
    }

    // 403 Forbidden 권한 부족 에러
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse> handleForbiddenException(AccessDeniedException e) {
        log.warn("AccessDeniedException Error", e);
        return ApiResponse.fail(GlobalErrorStatus._FORBIDDEN.getResponse());
    }

    // 404 Not Found 리소스 없음 에러
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse> handleEntityNotFoundException(NoHandlerFoundException e) {
        log.info("NoHandlerFoundException Error", e);
        return ApiResponse.fail(GlobalErrorStatus._NOT_FOUND.getResponse());
    }

    // 409 Conflict 서버와 현재 상태 충돌 에러
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse> handleConflictException(IllegalStateException e) {
        log.error("IllegalStateException Error", e);
        return ApiResponse.fail(GlobalErrorStatus._CONFLICT.getResponse());
    }

    // 비즈니스 로직 에러
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse> handleCustomException(BaseException e) {
        log.error("BusinessError Error");
        log.error(e.getMessage());
        return ApiResponse.fail(e.getMessage(), e.getHttpStatus());
    }

    //500 나머지 에러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleNullPointerException(Exception e, HttpServletRequest request){
        log.error("Exception Error", e);
        return ApiResponse.fail(GlobalErrorStatus._INTERNAL_SERVER_ERROR.getResponse());
    }
}

/**
 log.debug("디버그 메시지");
 log.info("정보 메시지");
 log.warn("경고 메시지");
 log.error("에러 메시지");
 **/