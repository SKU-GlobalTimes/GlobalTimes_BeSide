package com.example.globalTimes_be.domain.article.ApiDoc;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "메인 페이지", description = "메인페이지 관련 API입니다.")
public interface ArticleApiDocumentation {

    @Operation(summary = "기사 최신 순 조회", description = "최신 기사 목록을 조회하는 API (최신순 정렬)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회에 성공하였습니다."),
            @ApiResponse(responseCode = "500", description = "서버 오류가 발생하였습니다.")
    })
    ResponseEntity<?> getArticlesByPublishedAt(
            @Parameter(name = "page", description = "페이지 번호 (0부터 시작!)", example = "0") @RequestParam(name = "page") int page,
            @Parameter(name = "size", description = "페이지 크기", example = "10") @RequestParam(name = "size") int size);

    @Operation(summary = "기사 조회수 높은 순 조회", description = "조회수 높은 순 ( 랜딩페이지 Hot News 용)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회에 성공하였습니다."),
            @ApiResponse(responseCode = "500", description = "서버 오류가 발생하였습니다.")
    })
    public ResponseEntity<?> getArticlesByViewCount(
            @Parameter(name = "page", description = "페이지 번호 (0부터 시작!)", example = "0") @RequestParam(name = "page") int page,
            @Parameter(name = "size", description = "페이지 크기", example = "10") @RequestParam(name = "size") int size);;
}
