package com.example.globalTimes_be.global.exception;

import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalErrorHandlerTest {

    private final GlobalErrorHandler handler = new GlobalErrorHandler();

    @Test
    void taskRejectedReturnsServiceUnavailable() {
        ResponseEntity<ApiResponse> response = handler.handleTaskRejectedException(
                new TaskRejectedException("rejected")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void interruptedAsyncTaskReturnsServiceUnavailable() {
        Exception exception = new RuntimeException(new InterruptedException("async timeout"));

        ResponseEntity<ApiResponse> response = handler.handleNullPointerException(
                exception,
                mock(HttpServletRequest.class)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void unexpectedExceptionStillReturnsInternalServerError() {
        ResponseEntity<ApiResponse> response = handler.handleNullPointerException(
                new RuntimeException("unexpected"),
                mock(HttpServletRequest.class)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
