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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "실시간 검색어 페이지", description = "실시간 검색어 관련 API입니다.")
public interface TrendAiControllerDocs {

    @Operation(summary = "기사 요약",
            description = "해당 기사에 대한 요약을 한번에 반환합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "응답 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = com.example.globalTimes_be.global.apiPayload.code.ApiResponse.class),
                            examples = @ExampleObject(value = """
                                {
                                    "timestamp": "2025-04-01T15:41:45.2180857",
                                    "isSuccess": true,
                                    "message": "응답에 성공했습니다.",
                                    "data": "국가정보원이 최근 5년간 공공기관의 인공지능(AI) 정보화사업 실태를 조사하기 시작했으며, 이는 AI 기술의 안전한 운용과 보안 강화 방안을 모색하기 위한 차원이다. 이번 조사는 공공기관의 AI 활용 확대에 따른 보안 체계 정비를 목적으로 하고 있다."
                                }
                            """)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "크롤링 불가",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                {
                                     "timestamp": "2025-04-01T13:37:56.6982049",
                                     "isSuccess": false,
                                     "message": "해당 언론사는 요약 정보 제공이 불가능합니다. (크롤링 불가)"
                                 }
                                """)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "서버 에러가 발생하였습니다.",
                    content = @Content(mediaType = "application/json",
                            examples = {
                                @ExampleObject(name="서버 에러", value = """
                                    {
                                      "timestamp": "2024-10-30T15:38:12.43483271",
                                      "isSuccess": false,
                                      "message": "서버 에러가 발생하였습니다.",
                                      "data": null
                                    }
                                    """),
                                @ExampleObject(name = "gpt 에러", value = """
                                    {
                                      "timestamp": "2024-10-30T15:40:00.12345678",
                                      "isSuccess": false,
                                      "message": "GPT 요약 중 에러가 발생하였습니다.",
                                      "data": null
                                    }
                                    """)
                            }
                    )
            )
    })
    public ResponseEntity<?> getSummarizeTrendArticle(
            @Parameter(description = "뉴스기사 url", example = "https://www.etnews.com/20...")
            @NotNull(message = "뉴스기사 url은 비어있을 수 없습니다.")
            @PathVariable String url,

            @Parameter(description = "언어 설정 (기본값: 영어)", examples = {
                    @ExampleObject(name = "영어", value = "영어"),
                    @ExampleObject(name = "한국어", value = "한국어"),
                    @ExampleObject(name = "중국어", value = "중국어")})
            @RequestParam String language);

}
