package com.example.globalTimes_be.domain.test.controller;

import com.example.globalTimes_be.domain.test.exception.status.TestErrorStatus;
import com.example.globalTimes_be.domain.test.exception.status.TestSuccessStatus;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.exception.BaseException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/test")
@RestController
public class TestController implements TestControllerDocs{

    @GetMapping("/success")
    public ResponseEntity<ApiResponse> successTest() {
        return ApiResponse.success(TestSuccessStatus._SUCCESS_TEST.getResponse());
    }

    @GetMapping("/fail")
    public ResponseEntity<ApiResponse> failTest() {
        throw new BaseException(TestErrorStatus._CUSTOM_ERROR.getResponse());
    }
}
