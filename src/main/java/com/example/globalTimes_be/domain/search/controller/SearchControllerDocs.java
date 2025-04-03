package com.example.globalTimes_be.domain.search.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "검색 페이지", description = "검색 페이지 관련 API입니다.")
public interface SearchControllerDocs {
    @Operation(summary = "검색 결과에 대한 응답",
            description = "검색 결과에 따른 뉴스 기사 리스트(최대 100개) 응답 API")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "응답 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = com.example.globalTimes_be.global.apiPayload.code.ApiResponse.class),
                            examples = @ExampleObject(value = """
                                {
                                     "timestamp": "2025-04-03T15:28:18.3456286",
                                     "isSuccess": true,
                                     "message": "응답에 성공했습니다.",
                                     "data": {
                                         "originalText": "白色",
                                         "translatedText": "White",
                                         "searchArticles": [
                                             {
                                                 "id": 3,
                                                 "sourceName": "cnn",
                                                 "title": "Mikey Madison texts with government officials in ‘SNL’ cold open spoof of Signal group chat leak - CNN",
                                                 "description": "Oscar-winner Mikey Madison appeared during the cold open of the latest episode “Saturday Night Live,” which put a spin on the White House’s Signal group texting scandal.",
                                                 "urlToImage": "https://media.cnn.com/api/v1/images/stellar/prod/still-21501857-235681-66700000002-still.jpg?c=16x9&q=w_800,c_fill",
                                                 "publishedAt": "2025-03-30T04:24:00"
                                             },
                                             {
                                                 "id": 4,
                                                 "sourceName": "cbs-news",
                                                 "title": "Comedian Amber Ruffin pulled from White House Correspondents' Dinner - CBS News",
                                                 "description": "White House Correspondents Association President Eugene Daniels said that the WHCA board had \\"unanimously decided we are no longer featuring a comedic performance this year.\\"",
                                                 "urlToImage": "https://assets1.cbsnewsstatic.com/hub/i/r/2025/03/30/3b6c22cd-4725-4c08-88c0-c3225f224d99/thumbnail/1200x630/d1f191a5b437275a2a414f629d579810/gettyimages-2207109898.jpg?v=95354023eeb6141c58c08d9a5716f291",
                                                 "publishedAt": "2025-03-30T03:27:00"
                                             }
                                         ]
                                     }
                                 }
                            """)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "비즈니스 에러",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name= "검증에러", value = """
                                            {
                                                "timestamp": "2025-04-03T15:54:50.410816",
                                                "isSuccess": false,
                                                "message": "검색어는 비어있을 수 없습니다., 검색어는 최소 1자 이상이어야 합니다.",
                                                "data": null
                                            }
                                        """)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "서버 에러가 발생하였습니다.",
                    content = @Content(mediaType = "application/json",
                            examples = {@ExampleObject(name="서버에러", value = """
                                    {
                                      "timestamp": "2024-10-30T15:38:12.43483271",
                                      "isSuccess": false,
                                      "message": "서버 에러가 발생하였습니다.",
                                      "data": null
                                    }
                                    """),
                                    @ExampleObject(name="번역 에러", value = """
                                    {
                                      "timestamp": "2024-10-30T15:38:12.43483271",
                                      "isSuccess": false,
                                      "message": "구글 번역 중 에러가 발생하였습니다.",
                                      "data": null
                                    }
                                    """)
                            }
                    )
            ),
    })
    public ResponseEntity<?> getSearchArticles(
            @Parameter(description = "검색 문자열", example = "트럼프")
            @NotBlank(message = "검색어는 비어있을 수 없습니다.")
            @Size(min = 1, message = "검색어는 최소 1자 이상이어야 합니다.")
            @RequestParam String text);
}
