package com.example.globalTimes_be.domain.trend.dto.resonse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "실시간 검색 키워드 정보 DTO")
public class TrendDTO {
    @Schema(description = "실검 키워드", example = "차준환")
    private String keyword;

    @Schema(description = "기사 제목", example = "냉부해' 차준환, 금메달 식단 최초 공개 \"파스타는 10알만\"")
    private String title;

    @Schema(description = "언론사명", example = "네이트 뉴스")
    private String sourceName;

    @Schema(description = "원본 기사 url", example = "https://news.nate.com/view/20250330n11406")
    private String url;

    @Schema(description = "기사 이미지 url", example = "https://encrypted-tbn2.gsta...")
    private String urlToImage;

    // JSON 리스트 변환 메서드
    public static String toJson(List<TrendDTO> keywords) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(keywords);
    }

    // JSON 문자열을 객체 리스트로 변환
    public static List<TrendDTO> fromJson(String json) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return List.of(objectMapper.readValue(json, TrendDTO[].class));
    }
}
