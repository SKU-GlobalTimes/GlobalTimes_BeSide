package com.example.globalTimes_be.externalApi.config;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 각국 언론사 RSS 피드 수집 대상 설정.
 * 수집 대상 추가/제거 시 이 파일만 수정하면 된다.
 */
@Component
public class RssFeedConfig {

    public record FeedSource(
            String url,
            String country,
            String language,
            String sourceName,
            String category
    ) {}

    public List<FeedSource> getFeeds() {
        return List.of(
                // 한국 - 연합뉴스 (실제 경로 수정)
                new FeedSource("https://www.yna.co.kr/rss/news.xml", "kr", "ko", "연합뉴스", "general"),
                // 프랑스
                new FeedSource("https://www.france24.com/fr/rss", "fr", "fr", "France24", "general"),
                // 독일
                new FeedSource("https://rss.dw.com/rdf/rss-de-all", "de", "de", "Deutsche Welle", "general"),
                // 일본
                new FeedSource("https://www3.nhk.or.jp/rss/news/cat0.xml", "jp", "ja", "NHK", "general"),
                // 아랍어 - BBC Arabic (Al Jazeera/Al Arabiya 모두 봇 차단, BBC는 RSS 안정 제공)
                new FeedSource("https://feeds.bbci.co.uk/arabic/rss.xml", "sa", "ar", "BBC Arabic", "general"),
                // 중국 - South China Morning Post
                new FeedSource("https://www.scmp.com/rss/91/feed", "cn", "zh", "South China Morning Post", "general"),
                // 스페인어 - BBC Mundo (RT 대체, 접근 안정적)
                new FeedSource("https://feeds.bbci.co.uk/mundo/rss.xml", "es", "es", "BBC Mundo", "general")
        );
    }
}
