package com.example.globalTimes_be.domain.detail.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "상세 기사 페이지 응답 DTO")
public class DetailResDTO {
    @Schema(description = "상세 기사 데이터", implementation = DetailResponseDTO.class)
    private DetailResponseDTO newsDetail;

    @Schema(description = "최신 기사 리스트 데이터", implementation = RecentArticleDTO.class)
    private List<RecentArticleDTO> recentNewsList;
}
