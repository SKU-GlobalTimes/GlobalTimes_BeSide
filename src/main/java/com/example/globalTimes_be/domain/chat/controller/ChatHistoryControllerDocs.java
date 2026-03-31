package com.example.globalTimes_be.domain.chat.controller;

import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "ChatHistory", description = "GPT 질의 히스토리 API")
public interface ChatHistoryControllerDocs {

    @Operation(
            summary = "내 채팅 목록 조회 (팝업 리스트용)",
            description = "로그인한 사용자가 질의한 기사별 마지막 대화 미리보기 목록을 반환합니다. 최신 대화 순으로 정렬됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "message": "응답에 성공했습니다.",
                              "data": [
                                {
                                  "articleId": 42,
                                  "articleTitle": "BTS 공연 매진 기록...",
                                  "thumbnailUrl": "https://...",
                                  "lastQuestion": "이 기사의 핵심 내용은?",
                                  "lastAnswerPreview": "BTS의 이번 공연은 역대 최단 시간 매진을...",
                                  "lastChatAt": "2026-03-30T15:00:00"
                                }
                              ]
                            }
                            """))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    ResponseEntity<ApiResponse> getChatList(
            @Parameter(hidden = true) Authentication authentication
    );

    @Operation(
            summary = "기사별 대화 전체 조회 (상세 페이지용)",
            description = "특정 기사에 대해 로그인한 사용자가 나눈 GPT 대화 전체를 오래된 순으로 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "message": "응답에 성공했습니다.",
                              "data": [
                                {
                                  "chatId": 1,
                                  "question": "이 기사의 핵심 내용은?",
                                  "answer": "이 기사는 BTS의 공연이 역대 최단...",
                                  "createdAt": "2026-03-30T15:00:00"
                                }
                              ]
                            }
                            """))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    ResponseEntity<ApiResponse> getChatsByArticle(
            @Parameter(description = "기사 ID") Long articleId,
            @Parameter(hidden = true) Authentication authentication
    );
}
