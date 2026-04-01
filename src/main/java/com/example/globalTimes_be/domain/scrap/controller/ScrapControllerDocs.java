package com.example.globalTimes_be.domain.scrap.controller;

import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "스크랩", description = "스크랩 관련 API")
public interface ScrapControllerDocs {

    @Operation(summary = "스크랩 기사 정보 조회 (비로그인 호환)",
            description = "localStorage의 기사 ID 목록을 받아 기사 정보를 반환합니다. 비로그인 사용자용 기존 API 유지.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "응답 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                {
                                    "timestamp": "2025-04-03T10:38:27",
                                    "isSuccess": true,
                                    "message": "응답에 성공했습니다.",
                                    "data": [
                                        {
                                            "id": 1,
                                            "sourceName": "the-washington-post",
                                            "title": "Duke looks a cut above...",
                                            "description": "The Blue Devils were dominant...",
                                            "urlToImage": "https://example.com/image.jpg",
                                            "publishedAt": "2025-03-30T08:37:33"
                                        }
                                    ]
                                }
                            """)
                    )
            )
    })
    ResponseEntity<ApiResponse> getScrapArticle(
            @Parameter(description = "뉴스기사 ID 목록", example = "?id=1&id=2&id=4")
            @NotNull(message = "뉴스기사 ID는 비어있을 수 없습니다.")
            @RequestParam List<Long> id);

    @Operation(summary = "스크랩 토글 (추가/취소)",
            description = "기사를 스크랩하거나 취소합니다. 이미 스크랩된 경우 취소, 아닌 경우 추가됩니다. 로그인 필요.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토글 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "스크랩 추가", value = """
                                        {
                                            "timestamp": "2026-03-31T17:00:00",
                                            "isSuccess": true,
                                            "message": "응답에 성공했습니다.",
                                            "data": { "scrapped": true }
                                        }
                                    """),
                                    @ExampleObject(name = "스크랩 취소", value = """
                                        {
                                            "timestamp": "2026-03-31T17:00:00",
                                            "isSuccess": true,
                                            "message": "응답에 성공했습니다.",
                                            "data": { "scrapped": false }
                                        }
                                    """)
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    ResponseEntity<ApiResponse> toggleScrap(
            @Parameter(description = "기사 ID", example = "7285") @PathVariable("id") Long articleId,
            Authentication authentication);

    @Operation(summary = "내 스크랩 목록 조회",
            description = "로그인 유저의 스크랩 목록을 최신순으로 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                {
                                    "timestamp": "2026-03-31T17:00:00",
                                    "isSuccess": true,
                                    "message": "응답에 성공했습니다.",
                                    "data": [
                                        {
                                            "articleId": 7285,
                                            "title": "기사 제목...",
                                            "sourceName": "yna",
                                            "urlToImage": "https://example.com/photo.jpg",
                                            "description": "기사 설명...",
                                            "publishedAt": "2026-03-26T17:41:00",
                                            "scrappedAt": "2026-03-31T17:14:35"
                                        }
                                    ]
                                }
                            """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    ResponseEntity<ApiResponse> getMyScrapList(Authentication authentication);

    @Operation(summary = "특정 기사 스크랩 여부 조회",
            description = "로그인 유저가 특정 기사를 스크랩했는지 여부를 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                {
                                    "timestamp": "2026-03-31T17:00:00",
                                    "isSuccess": true,
                                    "message": "응답에 성공했습니다.",
                                    "data": { "scrapped": true }
                                }
                            """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    ResponseEntity<ApiResponse> getScrapStatus(
            @Parameter(description = "기사 ID", example = "7285") @PathVariable("id") Long articleId,
            Authentication authentication);
}
