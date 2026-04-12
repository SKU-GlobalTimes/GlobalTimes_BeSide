package com.example.globalTimes_be.domain.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "AI 기사 분석", description = "기사 요약 및 질의 관련 API입니다.")
public interface AiControllerDocs {
    @Operation(summary = "기사 요약",
            description = "해당 기사에 대한 요약을 한번에 반환합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "응답 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = com.example.globalTimes_be.global.apiPayload.code.ApiResponse.class),
                            examples = @ExampleObject(value = """
                                {
                                 "timestamp": "2025-03-28T17:13:13.7470472",
                                 "isSuccess": true,
                                 "message": "응답에 성공했습니다.",
                                 "data": "Richard Pitino, the men's basketball coach at the University of New Mexico, is leaving to become the head coach at Xavier University in the Big East Conference."
                             }
                            """)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "크롤링 불가 (본문 서두 제공)",
                    content = @Content(mediaType = "application/json",
                            examples = {@ExampleObject(value = """
                                    {
                                         "timestamp": "2025-04-01T13:37:56.6982049",
                                         "isSuccess": false,
                                         "message": "해당 언론사는 요약 정보 제공이 불가능합니다. (크롤링 불가)",
                                         "data": "NEWARK With a mastery of collaborative..."
                                     }
                                    """),
                                    @ExampleObject(name= "검증에러", value = """
                                        {
                                            "timestamp": "2025-04-03T15:54:50.410816",
                                            "isSuccess": false,
                                            "message": "뉴스기사 id는 비어있을 수 없습니다.",
                                            "data": null
                                        }
                                    """)
                            }
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
    public ResponseEntity<?> summarizeArticle(
            @Parameter(description = "뉴스기사 ID", example = "1")
            @NotNull(message = "뉴스기사 id는 비어있을 수 없습니다.")
            @PathVariable Long id,

            @Parameter(description = "언어 설정 (기본값: 영어)", examples = {
                    @ExampleObject(name = "영어", value = "영어"),
                    @ExampleObject(name = "한국어", value = "한국어"),
                    @ExampleObject(name = "중국어", value = "중국어")})
            @RequestParam String language);

    @Operation(
        summary = "기사 요약 스트리밍",
        description = "해당 기사에 대한 요약을 SSE 방식으로 실시간으로 스트리밍합니다.",
        responses = {
                @ApiResponse(responseCode = "200", description = "SSE 스트리밍 성공",
                        content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                                schema = @Schema(type = "string", description = "요약된 기사 내용 스트리밍"))
                ),
                @ApiResponse(responseCode = "400", description = "비즈니스 에러",
                        content = @Content(mediaType = "application/json",
                                examples = {@ExampleObject(name = "db에 기사정보 없음", value = """
                                            {
                                              "timestamp": "2024-10-30T15:38:12.43483271",
                                              "isSuccess": false,
                                              "message": "해당 기사의 정보가 없습니다.",
                                              "data": null
                                            }
                                        """),
                                        @ExampleObject(name= "크롤링에러", value = """
                                            {
                                              "timestamp": "2024-10-30T15:38:12.43483271",
                                              "isSuccess": false,
                                              "message": "해당 언론사는 요약 정보 제공이 불가능합니다. (크롤링 불가)",
                                              "data": null
                                            }
                                        """)
                                }
                        )
                ),
                @ApiResponse(responseCode = "500", description = "서버 에러가 발생하였습니다.",
                        content = @Content(mediaType = "application/json",
                                examples = @ExampleObject(value = """
                                {
                                  "timestamp": "2024-10-30T15:38:12.43483271",
                                  "isSuccess": false,
                                  "message": "서버 에러가 발생하였습니다.",
                                  "data": null
                                }
                                """)
                        )
                ),
        })
    public SseEmitter summarizeArticleSse(
            @Parameter(description = "뉴스기사 ID", example = "1")
            @NotNull(message = "뉴스기사 id는 비어있을 수 없습니다.")
            @PathVariable Long id,

            @Parameter(description = "언어 설정 (기본값: 영어)", examples = {
                    @ExampleObject(name = "영어", value = "영어"),
                    @ExampleObject(name = "한국어", value = "한국어"),
                    @ExampleObject(name = "중국어", value = "중국어")})
            @RequestParam String language
    );

    @Operation(
        summary = "기사에 대한 사용자 질문 답변 스트리밍",
        description = "해당 기사에 대한 정보를 바탕으로 사용자 질문에 대한 답변을 SSE 방식으로 실시간으로 스트리밍합니다.",
        responses = {
                @ApiResponse(responseCode = "200", description = "SSE 스트리밍 성공",
                        content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                                schema = @Schema(type = "string", description = "기사 기반 질의응답 스트리밍"))
                ),
                @ApiResponse(responseCode = "400", description = "비즈니스 에러",
                        content = @Content(mediaType = "application/json",
                                examples = {@ExampleObject(name = "db에 기사정보 없음", value = """
                                            {
                                              "timestamp": "2024-10-30T15:38:12.43483271",
                                              "isSuccess": false,
                                              "message": "해당 기사의 정보가 없습니다.",
                                              "data": null
                                            }
                                        """),
                                        @ExampleObject(name= "크롤링에러", value = """
                                            {
                                              "timestamp": "2024-10-30T15:38:12.43483271",
                                              "isSuccess": false,
                                              "message": "해당 언론사는 요약 정보 제공이 불가능합니다. (크롤링 불가)",
                                              "data": null
                                            }
                                        """),
                                        @ExampleObject(name= "검증에러", value = """
                                            {
                                                "timestamp": "2025-04-03T15:54:50.410816",
                                                "isSuccess": false,
                                                "message": "뉴스기사 id는 비어있을 수 없습니다., 질문은 비어있을 수 없습니다., 질문은 최소 1자 이상이어야 합니다.",
                                                "data": null
                                            }
                                        """)
                                }
                        )
                ),
                @ApiResponse(responseCode = "500", description = "서버 에러가 발생하였습니다.",
                        content = @Content(mediaType = "application/json",
                                examples = @ExampleObject(value = """
                                {
                                  "timestamp": "2024-10-30T15:38:12.43483271",
                                  "isSuccess": false,
                                  "message": "서버 에러가 발생하였습니다.",
                                  "data": null
                                }
                                """)
                        )
                ),
        })
    public SseEmitter askArticle(
            @Parameter(description = "뉴스기사 ID", example = "1")
            @NotNull(message = "뉴스기사 id는 비어있을 수 없습니다.")
            @PathVariable Long id,

            @Parameter(description = "사용자 질문", example = "기사에서 키워드를 뽑아줘.")
            @NotBlank(message = "질문은 비어있을 수 없습니다.")
            @Size(min = 1, message = "질문은 최소 1자 이상이어야 합니다.")
            @RequestParam String question,

            @Parameter(description = "비로그인 시 대화 맥락·Redis 저장용 UUID (헤더). EventSource는 헤더 불가 시 쿼리 anonymousSession 사용.", example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader(value = "X-Anonymous-Session", required = false) String anonymousSessionHeader,

            @Parameter(description = "비로그인 세션 UUID (쿼리). 헤더와 동일 우선순위: 헤더가 있으면 헤더 사용.")
            @RequestParam(value = "anonymousSession", required = false) String anonymousSessionQuery,

            @Parameter(hidden = true) Authentication authentication);

    @Operation(
            summary = "비로그인 기사 채팅 히스토리 조회",
            description = "X-Anonymous-Session(UUID)과 기사 ID로 Redis에 저장된 질문·답변 목록을 반환합니다. 세션이 없거나 형식이 잘못되면 빈 배열입니다."
    )
    @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = com.example.globalTimes_be.global.apiPayload.code.ApiResponse.class)))
    ResponseEntity<?> getAnonymousArticleChatHistory(
            @Parameter(description = "뉴스기사 ID", example = "1") @PathVariable Long id,
            @Parameter(description = "비로그인 세션 UUID (헤더)") @RequestHeader(value = "X-Anonymous-Session", required = false) String anonymousSessionHeader,
            @Parameter(description = "비로그인 세션 UUID (쿼리)") @RequestParam(value = "anonymousSession", required = false) String anonymousSessionQuery
    );
}
