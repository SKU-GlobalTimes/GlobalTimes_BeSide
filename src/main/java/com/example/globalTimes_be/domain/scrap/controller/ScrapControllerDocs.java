package com.example.globalTimes_be.domain.scrap.controller;

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

import java.util.List;

@Tag(name = "마이스크랩 페이지", description = "마이스크랩 페이지 관련 API입니다.")
public interface ScrapControllerDocs {
    @Operation(summary = "마이스크랩 기사 데이터 응답",
            description = "마이스크랩에 대한 기사의 간략한 정보 응답 API")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "응답 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = com.example.globalTimes_be.global.apiPayload.code.ApiResponse.class),
                            examples = @ExampleObject(value = """
                                {
                                     "timestamp": "2025-04-03T10:38:27.3875865",
                                     "isSuccess": true,
                                     "message": "응답에 성공했습니다.",
                                     "data": [
                                         {
                                             "id": 1,
                                             "sourceName": "the-washington-post",
                                             "title": "Duke looks a cut above as it beats Alabama to reach 18th Final Four - The Washington Post",
                                             "description": "The Blue Devils were dominant in the East Region final Saturday night, pulling away from the Crimson Tide, 85-65.",
                                             "urlToImage": "https://www.washingtonpost.com/wp-apps/imrs.php?src=https://arc-anglerfish-washpost-prod-washpost.s3.amazonaws.com/public/HGUFEQVDJCK5ORKRD6ASZKBICE_size-normalized.JPG&w=1440",
                                             "publishedAt": "2025-03-30T08:37:33"
                                         },
                                         {
                                             "id": 2,
                                             "sourceName": "cnn",
                                             "title": "Trial will determine who will pay $600 million settlement in disastrous Norfolk Southern derailment - Yahoo",
                                             "description": "Norfolk Southern wants two other companies to help pay for the $600 million class-action settlement it agreed to over its disastrous 2023 train derailment near the Ohio-Pennsylvania border and the toxic chemicals that were released and burned.",
                                             "urlToImage": "https://media.cnn.com/api/v1/images/stellar/prod/ap25087719392130.jpg?c=16x9&q=w_800,c_fill",
                                             "publishedAt": "2025-03-30T06:08:00"
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
                            """)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "서버 에러가 발생하였습니다.",
                    content = @Content(mediaType = "application/json",
                            examples = {@ExampleObject(name = "서버에러", value = """
                                    {
                                      "timestamp": "2024-10-30T15:38:12.43483271",
                                      "isSuccess": false,
                                      "message": "서버 에러가 발생하였습니다.",
                                      "data": null
                                    }
                                    """),
                                    @ExampleObject(name = "기사정보 없음 에러", value = """
                                            {
                                              "timestamp": "2024-10-30T15:38:12.43483271",
                                              "isSuccess": false,
                                              "message": "요청한 기사에 대한 정보가 없습니다.",
                                              "data": null
                                            }
                                    """)
                            }
                    )
            ),
    })
    public ResponseEntity<?> getScrapArticle(
            @Parameter(description = "뉴스기사 ID", example = "?id=1&id=2&id=4")
            @NotNull(message = "뉴스기사id는 비어있을 수 없습니다.")
            @RequestParam List<Long> id);
}
