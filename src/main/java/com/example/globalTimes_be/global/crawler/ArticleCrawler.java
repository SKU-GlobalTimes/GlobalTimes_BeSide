package com.example.globalTimes_be.global.crawler;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
public class ArticleCrawler {

    private final int timeoutMs;
    private final String userAgent;

    public ArticleCrawler(
            @Value("${crawler.timeout-ms:5000}") int timeoutMs,
            @Value("${crawler.user-agent:Mozilla/5.0 (compatible; GlobalTimesBot/1.0)}") String userAgent
    ) {
        this.timeoutMs = timeoutMs;
        this.userAgent = userAgent;
    }

    public Optional<String> crawlParagraphs(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .get();

            String content = extractParagraphs(doc);
            if (content == null || content.isBlank()) {
                log.warn("[Crawler] 본문 추출 실패: paragraphs empty url={}", url);
                return Optional.empty();
            }

            return Optional.of(content);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("[Crawler] 크롤링 실패: url={}, message={}", url, e.getMessage());
            return Optional.empty();
        }
    }

    String extractParagraphs(Document doc) {
        Elements paragraphs = doc.select("p");
        return paragraphs.stream()
                .map(Element::text)
                .filter(text -> !text.isBlank())
                .reduce((p1, p2) -> p1 + "\n" + p2)
                .orElse(null);
    }
}
