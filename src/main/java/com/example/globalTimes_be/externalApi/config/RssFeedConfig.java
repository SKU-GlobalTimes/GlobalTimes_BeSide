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

                // ── 영국 / BBC English ────────────────────────────────────────
                new FeedSource("https://feeds.bbci.co.uk/news/world/rss.xml",                    "gb", "en", "BBC",  "general"),
                new FeedSource("https://feeds.bbci.co.uk/news/business/rss.xml",                 "gb", "en", "BBC",  "business"),
                new FeedSource("https://feeds.bbci.co.uk/news/technology/rss.xml",               "gb", "en", "BBC",  "technology"),
                new FeedSource("https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",  "gb", "en", "BBC",  "science"),
                new FeedSource("https://feeds.bbci.co.uk/news/health/rss.xml",                   "gb", "en", "BBC",  "health"),
                new FeedSource("https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml",   "gb", "en", "BBC",  "entertainment"),
                new FeedSource("https://feeds.bbci.co.uk/sport/rss.xml",                         "gb", "en", "BBC",  "sports"),

                // ── 한국 / 연합뉴스 ───────────────────────────────────────────
                new FeedSource("https://www.yna.co.kr/rss/news.xml",          "kr", "ko", "연합뉴스", "general"),
                new FeedSource("https://www.yna.co.kr/rss/economy.xml",       "kr", "ko", "연합뉴스", "business"),
                new FeedSource("https://www.yna.co.kr/rss/sports.xml",        "kr", "ko", "연합뉴스", "sports"),
                new FeedSource("https://www.yna.co.kr/rss/culture.xml",       "kr", "ko", "연합뉴스", "entertainment"),

                // ── 일본 / NHK ────────────────────────────────────────────────
                new FeedSource("https://www3.nhk.or.jp/rss/news/cat0.xml", "jp", "ja", "NHK", "general"),
                new FeedSource("https://www3.nhk.or.jp/rss/news/cat4.xml", "jp", "ja", "NHK", "business"),
                new FeedSource("https://www3.nhk.or.jp/rss/news/cat3.xml", "jp", "ja", "NHK", "science"),
                new FeedSource("https://www3.nhk.or.jp/rss/news/cat6.xml", "jp", "ja", "NHK", "sports"),

                // ── 프랑스 / France24 ─────────────────────────────────────────
                new FeedSource("https://www.france24.com/fr/rss",              "fr", "fr", "France24", "general"),
                new FeedSource("https://www.france24.com/fr/economie/rss",     "fr", "fr", "France24", "business"),
                new FeedSource("https://www.france24.com/fr/sports/rss",       "fr", "fr", "France24", "sports"),
                new FeedSource("https://www.france24.com/fr/technologies/rss", "fr", "fr", "France24", "technology"),

                // ── 독일 / Deutsche Welle ─────────────────────────────────────
                new FeedSource("https://rss.dw.com/rdf/rss-de-all", "de", "de", "Deutsche Welle", "general"),

                // ── 아랍어 / BBC Arabic ───────────────────────────────────────
                new FeedSource("https://feeds.bbci.co.uk/arabic/rss.xml",              "sa", "ar", "BBC Arabic", "general"),
                new FeedSource("https://feeds.bbci.co.uk/arabic/middleeast/rss.xml",   "sa", "ar", "BBC Arabic", "business"),

                // ── 중국 / South China Morning Post ───────────────────────────
                new FeedSource("https://www.scmp.com/rss/91/feed",  "cn", "zh", "South China Morning Post", "general"),
                new FeedSource("https://www.scmp.com/rss/92/feed",  "cn", "zh", "South China Morning Post", "business"),
                new FeedSource("https://www.scmp.com/rss/36/feed",  "cn", "zh", "South China Morning Post", "technology"),
                new FeedSource("https://www.scmp.com/rss/95/feed",  "cn", "zh", "South China Morning Post", "sports"),

                // ── 스페인어 / BBC Mundo ──────────────────────────────────────
                new FeedSource("https://feeds.bbci.co.uk/mundo/rss.xml", "es", "es", "BBC Mundo", "general")
        );
    }
}
