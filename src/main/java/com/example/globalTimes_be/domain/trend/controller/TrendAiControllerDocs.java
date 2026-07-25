package com.example.globalTimes_be.domain.trend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "실시간 검색어 페이지", description = "실시간 검색어 관련 API")
public interface TrendAiControllerDocs {

    @Operation(
            summary = "Trend 기사 요약",
            description = "기사 본문을 요청한 언어로 두 문장 이내로 요약합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "요약 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = com.example.globalTimes_be.global.apiPayload.code.ApiResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "기사 크롤링 불가",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "message": "해당 언론사는 요약 정보 제공이 불가능합니다. (크롤링 불가)"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "외부 AI 요약 서비스 응답 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "message": "외부 AI 요약 서비스 응답에 실패했습니다."
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "504",
                    description = "외부 AI 요약 서비스 응답 시간 초과",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "message": "외부 AI 요약 서비스 응답 시간이 초과되었습니다."
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "AI 요약 내부 처리 오류",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "message": "AI 요약 처리 중 내부 오류가 발생했습니다."
                                    }
                                    """)
                    )
            )
    })
    ResponseEntity<?> getSummarizeTrendArticle(
            @Parameter(description = "뉴스 기사 URL", example = "https://www.example.com/news/1")
            @NotNull(message = "뉴스 기사 URL은 필수입니다.")
            @RequestParam String url,

            @Parameter(description = "요약 언어", example = "Korean")
            @RequestParam String language
    );
}
