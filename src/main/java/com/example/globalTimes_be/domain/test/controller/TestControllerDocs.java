package com.example.globalTimes_be.domain.test.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

public interface TestControllerDocs {

    @Operation(summary = "Test 컨트롤러 응답",
            description = "Test 컨트롤러 응답 요청을 통해 서버가 정상적으로 작동하는지 확인<br>"
                        + "성공시 응답 데이터 포맷 확인")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "테스트 응답 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\n"
                                    + "    \"timestamp\": \"2024-10-22T21:35:03.755865\",\n"
                                    + "    \"isSuccess\": true,\n"
                                    + "    \"message\": \"성공 테스트 확인\",\n"
                                    + "    \"data\": null\n"
                                    + "}")
                            )),
            @ApiResponse(responseCode = "500", description = "잘못된 요청입니다.",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\n"
                                    + "    \"timestamp\": \"2024-10-22T21:35:03.755865\",\n"
                                    + "    \"isSuccess\": false,\n"
                                    + "    \"message\": \"서버 에러가 발생하였습니다.\",\n"
                                    + "    \"data\": null\n"
                                    + "}")
            )),
    })
    public ResponseEntity<?> successTest();

    @Operation(summary = "Test 컨트롤러 응답",
            description = "Test 컨트롤러 응답 요청을 통해 서버가 정상적으로 작동하는지 확인<br>"
                    + "실패시 응답 데이터 포맷 확인")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "400", description = "잘못된 요청입니다.",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\n"
                                    + "    \"timestamp\": \"2024-10-22T21:35:03.755865\",\n"
                                    + "    \"isSuccess\": false,\n"
                                    + "    \"message\": \"에러테스트 요청입니다.\",\n"
                                    + "    \"data\": null\n"
                                    + "}")
                    )),

            @ApiResponse(responseCode = "500", description = "잘못된 요청입니다.",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\n"
                                    + "    \"timestamp\": \"2024-10-22T21:35:03.755865\",\n"
                                    + "    \"isSuccess\": false,\n"
                                    + "    \"message\": \"서버 에러가 발생하였습니다.\",\n"
                                    + "    \"data\": null\n"
                                    + "}")
                    )),
    })
    public ResponseEntity<?> failTest();

}
