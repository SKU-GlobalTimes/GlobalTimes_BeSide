package com.example.globalTimes_be.externalApi.config;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * News API 수집 대상(국가/카테고리/도메인) 설정을 한 곳에서 관리한다.
 * 수집 대상을 추가/제거할 때는 이 파일만 수정하면 된다.
 */
@Component
public class NewsFetchConfig {

    /**
     * 헤드라인 수집 대상 국가 코드 목록 (News API top-headlines 기준)
     * 지원 코드: https://newsapi.org/docs/endpoints/top-headlines
     */
    public List<String> getCountries() {
        return List.of(
                "us",   // 미국
                "gb",   // 영국
                "fr",   // 프랑스
                "de",   // 독일
                "kr"    // 한국
        );
    }

    /**
     * 수집 카테고리 목록 (null = general)
     */
    public List<String> getCategories() {
        return List.of(
                "general",
                "business",
                "entertainment",
                "health",
                "science",
                "sports",
                "technology"
        );
    }

    /**
     * Everything API 수집 대상 도메인 목록
     * 국가별 대표 미디어 도메인을 포함한다.
     */
    public List<String> getDomains() {
        return List.of(
                "wsj.com",          // 미국 - Wall Street Journal
                "bbc.co.uk",        // 영국 - BBC
                "techcrunch.com",   // 미국 - TechCrunch
                "theguardian.com",  // 영국 - The Guardian
                "reuters.com",      // 글로벌 - Reuters
                "aljazeera.com"     // 중동 - Al Jazeera
        );
    }

    /**
     * 한 번 요청 시 가져올 최대 기사 수 (News API 최대: 100)
     */
    public int getPageSize() {
        return 100;
    }

    /**
     * 수집 실행 1회당 최대 API 요청 수 제한
     * 무료 플랜: 100 req/24h → 초기 적재(1회) + 스케줄링(2~3회) 고려해 여유 있게 설정
     */
    public int getMaxRequestsPerRun() {
        return 30;
    }

    /**
     * API 요청 간 딜레이 (ms)
     * rate limit 방지를 위해 요청 사이에 짧은 대기 시간 추가
     */
    public long getRequestDelayMs() {
        return 200;
    }
}
