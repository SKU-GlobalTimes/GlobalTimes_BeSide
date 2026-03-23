package com.example.globalTimes_be.domain.article.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CursorArticleResponseDto {

    private List<ArticleResponseDto> articles;

    // 다음 페이지 요청 시 사용할 커서값 (마지막 기사의 publishedAt ISO 문자열, 없으면 null)
    private String nextCursor;

    // 다음 페이지 존재 여부
    private boolean hasNext;
}
