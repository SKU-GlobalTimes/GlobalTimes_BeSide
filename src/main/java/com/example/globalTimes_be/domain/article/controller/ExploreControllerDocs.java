package com.example.globalTimes_be.domain.article.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "기사 탐색", description = "국가·카테고리·날짜 조건 기반 기사 탐색 API (검색과 별개)")
public interface ExploreControllerDocs {

    @Operation(
            summary = "기사 탐색 (필터 + 커서 페이징)",
            description = "country / category / date 조건을 조합해 기사를 탐색합니다. " +
                    "파라미터는 전부 선택값이며, 아무것도 없으면 전체 기사를 최신순으로 반환합니다. " +
                    "date는 yyyy-MM-dd 형식 (해당 날짜 하루치 기사), cursor는 이전 응답의 nextCursor 값을 사용합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "응답 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = com.example.globalTimes_be.global.apiPayload.code.ApiResponse.class),
                            examples = @ExampleObject(value = """
                                {
                                    "timestamp": "2026-03-27T10:00:00",
                                    "isSuccess": true,
                                    "message": "응답에 성공했습니다.",
                                    "data": {
                                        "articles": [
                                            {
                                                "id": 100,
                                                "sourceName": "연합뉴스",
                                                "publishedAt": "2 hours ago",
                                                "title": "한국 경제 동향",
                                                "description": "...",
                                                "urlToImage": "https://example.com/img.jpg",
                                                "viewCount": 42
                                            }
                                        ],
                                        "nextCursor": "2026-03-27T08:00:00",
                                        "hasNext": true
                                    }
                                }
                            """)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "서버 에러",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                {
                                    "timestamp": "2026-03-27T10:00:00",
                                    "isSuccess": false,
                                    "message": "서버 에러가 발생하였습니다.",
                                    "data": null
                                }
                            """)
                    )
            )
    })
    ResponseEntity<?> explore(
            @Parameter(description = "국가 코드 (예: kr, us, jp, de)", example = "kr")
            @RequestParam(required = false) String country,

            @Parameter(description = "카테고리 (예: general, business, sports, technology, health, entertainment, science, politics)", example = "sports")
            @RequestParam(required = false) String category,

            @Parameter(description = "날짜 필터 (yyyy-MM-dd), 해당 날짜 하루치 기사만 반환", example = "2026-03-27")
            @RequestParam(required = false) String date,

            @Parameter(description = "커서 (이전 응답의 nextCursor 값, 첫 요청 시 생략)", example = "2026-03-27T08:00:00")
            @RequestParam(required = false) String cursor,

            @Parameter(description = "페이지 크기 (기본값 20)", example = "20")
            @RequestParam(defaultValue = "20") int size
    );
}
