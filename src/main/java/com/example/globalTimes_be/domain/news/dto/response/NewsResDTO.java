package com.example.globalTimes_be.domain.news.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "상세 뉴스 페이지 응답 DTO")
public class NewsResDTO {
    @Schema(description = "상세 뉴스 페이지 데이터", implementation = NewsDetailDTO.class)
    private NewsDetailDTO newsDetail;

    @Schema(description = "최신 뉴스 리스트 데이터", implementation = RecentNewsDTO.class)
    private List<RecentNewsDTO> recentNewsList;
}
