package com.example.globalTimes_be.domain.detail.controller;

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

@Tag(name = "뉴스 상세 페이지", description = "뉴스 상세페이지 관련 API입니다.")
public interface DetailControllerDocs {
    @Operation(summary = "뉴스 상세정보 및 최신 뉴스 기사 응답",
            description = "해당 뉴스에 대한 상세 정보와 최신 뉴스 기사 리스트(최대 20개) 응답 API")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "응답 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = com.example.globalTimes_be.global.apiPayload.code.ApiResponse.class),
                            examples = @ExampleObject(value = """
                                {
                                    "timestamp": "2025-03-28T10:24:28.6081968",
                                    "isSuccess": true,
                                    "message": "응답에 성공했습니다.",
                                    "data": {
                                        "newsDetail": {
                                            "sourceName": "Politico",
                                            "author": "Seb Starcevic",
                                            "title": "Trump floats possibility of compensation for Jan. 6 rioters - POLITICO",
                                            "viewCount": 1,
                                            "url": "https://www.politico.com/news/2025/03/25/trump-floats-possibility-of-compensation-for-jan-6-rioters-00250063",
                                            "urlToImage": "https://static.politico.com/7a/af/06aa55284352997b83a000983381/trump-63887.jpg",
                                            "publishedAt": "2025-03-26T03:00:31Z"
                                        },
                                        "recentNewsList": [
                                            {
                                                "id": 1,
                                                "sourceName": "Politico",
                                                "title": "Trump floats possibility of compensation for Jan. 6 rioters - POLITICO",
                                                "urlToImage": "https://static.politico.com/7a/af/06aa55284352997b83a000983381/trump-63887.jpg",
                                                "publishedAt": "2025-03-26T03:00:31Z"
                                            }
                                        ]
                                    }
                                }
                            """)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "비즈니스 에러",
                    content = @Content(mediaType = "application/json",
                            examples = {@ExampleObject(name="기사 정보 없음", value = """
                                        {
                                          "timestamp": "2024-10-30T15:38:12.43483271",
                                          "isSuccess": false,
                                          "message": "해당 기사의 정보가 없습니다.",
                                          "data": null
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
    public ResponseEntity<?> getNewsDetail(
            @Parameter(description = "뉴스기사 ID", example = "1")
            @NotNull(message = "뉴스기사id는 비어있을 수 없습니다.")
            @RequestParam("id") Long id);

    @Operation(summary = "국가별 시각 비교 기사 응답",
            description = "해당 기사의 키워드를 기반으로 다른 국가의 관련 기사 목록을 반환합니다. (키워드 매칭 방식)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "응답 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = com.example.globalTimes_be.global.apiPayload.code.ApiResponse.class),
                            examples = @ExampleObject(value = """
                                {
                                    "timestamp": "2025-03-28T10:24:28.6081968",
                                    "isSuccess": true,
                                    "message": "응답에 성공했습니다.",
                                    "data": {
                                        "keyword": "Trump",
                                        "perspectives": {
                                            "US": [
                                                {
                                                    "id": 10,
                                                    "country": "US",
                                                    "language": "en",
                                                    "source": "CNN",
                                                    "title": "Trump signs executive order on trade",
                                                    "description": "President Trump signed...",
                                                    "urlToImage": "https://example.com/img.jpg",
                                                    "publishedAt": "2025-03-27T08:00:00"
                                                }
                                            ],
                                            "KR": [
                                                {
                                                    "id": 42,
                                                    "country": "KR",
                                                    "language": "ko",
                                                    "source": "연합뉴스",
                                                    "title": "트럼프 행정명령 서명",
                                                    "description": "트럼프 대통령이...",
                                                    "urlToImage": "https://example.com/img2.jpg",
                                                    "publishedAt": "2025-03-27T09:00:00"
                                                }
                                            ]
                                        },
                                        "countriesFound": 2,
                                        "totalArticles": 2
                                    }
                                }
                            """)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "비즈니스 에러",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "기사 정보 없음", value = """
                                {
                                    "timestamp": "2025-03-28T10:24:28.6081968",
                                    "isSuccess": false,
                                    "message": "해당 기사의 정보가 없습니다.",
                                    "data": null
                                }
                                """)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "서버 에러가 발생하였습니다.",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                {
                                    "timestamp": "2025-03-28T10:24:28.6081968",
                                    "isSuccess": false,
                                    "message": "서버 에러가 발생하였습니다.",
                                    "data": null
                                }
                                """)
                    )
            )
    })
    public ResponseEntity<?> getPerspectives(
            @Parameter(description = "뉴스기사 ID", example = "1")
            @PathVariable Long id);
}
