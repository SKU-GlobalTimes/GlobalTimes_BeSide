package com.example.globalTimes_be.domain.detail.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerspectivesResDTO {

    // 탐색에 사용된 키워드 (로깅/FE 표시용)
    private String keyword;

    // 국가코드 → 관련 기사 목록
    private Map<String, List<PerspectiveArticleDTO>> perspectives;

    // 결과가 있는 국가 수
    private int countriesFound;

    // 전체 관련 기사 수
    private int totalArticles;
}
