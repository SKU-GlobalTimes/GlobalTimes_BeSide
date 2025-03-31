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

@Tag(name = "실시간 검색어", description = "실시간 검색어 관련 API입니다.")
public interface TrendControllerDocs {

    @Operation(summary = "나라별 실시간 검색어 데이터 응답",
            description = "국가코드에 따른 실시간 검색어, 관련 기사 정보 리스트 (최대6개) 응답 API")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "응답 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = com.example.globalTimes_be.global.apiPayload.code.ApiResponse.class),
                            examples = @ExampleObject(value = """
                                        {
                                             "timestamp": "2025-03-31T14:31:13.2722883",
                                             "isSuccess": true,
                                             "message": "응답에 성공했습니다.",
                                             "data": [
                                                 {
                                                     "keyword": "weather news",
                                                     "title": "First Alert Weather: Heavy rain & severe storms possible on Monday morning",
                                                     "sourceName": "WBRC",
                                                     "url": "https://www.wbrc.com/2025/03/30/first-alert-weather-heavy-rain-severe-storms-possible-monday-morning/",
                                                     "urlToImage": "https://encrypted-tbn3.gstatic.com/images?q=tbn:ANd9GcS6_sNf3lEpvKEk20oNjuPYFkT8r6-ce70XYE1TennanKqnfCuCPL6SumAUMQU"
                                                 },
                                                 {
                                                     "keyword": "climate change",
                                                     "title": "Is Champagne’s Bubble About to Burst?",
                                                     "sourceName": "Bloomberg.com",
                                                     "url": "https://www.bloomberg.com/opinion/features/2025-03-30/can-champagne-in-france-survive-climate-change",
                                                     "urlToImage": "https://encrypted-tbn3.gstatic.com/images?q=tbn:ANd9GcSdTIXUeRZqS-wkuiTRQ0--vZYVEvTUBI-mByWHhUMT_Sv7oHNoDK_AxHGnvhY"
                                                 }
                                             ]
                                         }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "잘못된 국가 코드입니다.",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                {
                                  "timestamp": "2024-10-30T15:38:12.43483271",
                                  "isSuccess": false,
                                  "message": "잘못된 국가 코드입니다.",
                                  "data": null
                                }
                                """)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "역직렬화에 실패하였습니다.",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                {
                                  "timestamp": "2024-10-30T15:38:12.43483271",
                                  "isSuccess": false,
                                  "message": "역직렬화에 실패하였습니다.",
                                  "data": null
                                }
                                """)
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
    public ResponseEntity<?> getTrend(
            @Parameter(description = "국가별 코드", examples = {
                    @ExampleObject(name = "KR", value = "한국"),
                    @ExampleObject(name = "AU", value = "호주"),
                    @ExampleObject(name = "AT", value = "오스트리아"),
                    @ExampleObject(name = "BR", value = "브라질"),
                    @ExampleObject(name = "CA", value = "캐나다"),
                    @ExampleObject(name = "CO", value = "콜롬비아"),
                    @ExampleObject(name = "DK", value = "덴마크"),
                    @ExampleObject(name = "EG", value = "이집트"),
                    @ExampleObject(name = "FR", value = "프랑스"),
                    @ExampleObject(name = "DE", value = "독일"),
                    @ExampleObject(name = "GR", value = "그리스"),
                    @ExampleObject(name = "HK", value = "홍콩"),
                    @ExampleObject(name = "IN", value = "인도"),
                    @ExampleObject(name = "ID", value = "인도네시아"),
                    @ExampleObject(name = "IT", value = "이탈리아"),
                    @ExampleObject(name = "JP", value = "일본"),
                    @ExampleObject(name = "MY", value = "말레이시아"),
                    @ExampleObject(name = "MX", value = "멕시코"),
                    @ExampleObject(name = "NL", value = "네덜란드"),
                    @ExampleObject(name = "RU", value = "러시아"),
                    @ExampleObject(name = "SG", value = "싱가포르"),
                    @ExampleObject(name = "TW", value = "대만"),
                    @ExampleObject(name = "TR", value = "터키"),
                    @ExampleObject(name = "GB", value = "영국"),
                    @ExampleObject(name = "US", value = "미국"),
                    @ExampleObject(name = "ES", value = "스페인")
            })
            @NotNull(message = "국가코드는 비어있을 수 없습니다.")
            @RequestParam String code);
}
