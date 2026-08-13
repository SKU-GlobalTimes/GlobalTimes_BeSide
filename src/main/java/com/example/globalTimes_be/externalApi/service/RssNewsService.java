package com.example.globalTimes_be.externalApi.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.source.entity.Source;
import com.example.globalTimes_be.domain.source.service.SourceService;
import com.example.globalTimes_be.externalApi.config.RssFeedConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RssNewsService {

    private final ArticleRepository articleRepository;
    private final SourceService sourceService;
    private final RssFeedConfig rssFeedConfig;

    @Value("${news-fetch.rss-enabled:${news-fetch.enabled:false}}")
    private boolean fetchEnabled;

    @Value("${news-fetch.rss-countries:}")
    private String fetchCountries;

    @Value("${news-fetch.rss-max-feeds-per-run:0}")
    private int maxFeedsPerRun;

    @Value("${news-fetch.rss-max-articles-per-feed:0}")
    private int maxArticlesPerFeed;

    // RSS pubDate 파싱 포맷 목록 (언론사마다 포맷이 다름)
    // zzz = GMT/UTC 같은 이름형 타임존, Z = +0900/+0000 같은 숫자형 오프셋
    // ISO_OFFSET_DATE_TIME = DW 등 RDF 피드의 dc:date 형식 (2026-03-19T08:00:00+00:00)
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ISO_OFFSET_DATE_TIME
    );

    @PostConstruct
    public void init() {
        if (!fetchEnabled) {
            log.info("[RSS 초기화] RSS 수집 비활성화 → 건너뜀");
            return;
        }
        fetchAllFeeds("[RSS 초기 적재]");
    }

    // 6시간마다 RSS 수집
    @Scheduled(cron = "0 0 0/6 * * *")
    public void scheduledFetch() {
        if (!fetchEnabled) return;
        fetchAllFeeds("[RSS 스케줄링]");
    }

    private void fetchAllFeeds(String logPrefix) {
        int totalSaved = 0;
        for (RssFeedConfig.FeedSource feed : selectedFeeds()) {
            totalSaved += fetchFeed(feed);
        }
        log.info("{} 전체 저장된 신규 기사: {}개", logPrefix, totalSaved);
    }

    private int fetchFeed(RssFeedConfig.FeedSource feed) {
        try {
            // Jsoup으로 직접 fetch: Content-Type 비표준/User-Agent 문제를 RestTemplate보다 유연하게 처리
            String xml = Jsoup.connect(feed.url())
                    .userAgent("Mozilla/5.0 (compatible; GlobalTimesBot/1.0)")
                    .timeout(10_000)
                    .ignoreContentType(true)
                    .execute()
                    .body();

            if (xml == null || xml.isBlank()) {
                log.info("[수집 통계] {} | 응답 없음", feed.sourceName());
                return 0;
            }

            Document doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser());
            Elements items = limitArticles(doc.select("item"));

            if (items.isEmpty()) {
                log.info("[수집 통계] {} | 응답 없음", feed.sourceName());
                return 0;
            }

            return processItems(feed, items);

        } catch (Exception e) {
            log.error("[RSS 수집 오류] {} 처리 중 오류 발생", feed.sourceName(), e);
            return 0;
        }
    }

    List<RssFeedConfig.FeedSource> selectedFeeds() {
        String configuredCountries = fetchCountries == null ? "" : fetchCountries;
        Set<String> countries = Arrays.stream(configuredCountries.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        var feeds = rssFeedConfig.getFeeds().stream()
                .filter(feed -> countries.isEmpty()
                        || countries.contains(feed.country().toLowerCase(Locale.ROOT)));
        if (maxFeedsPerRun > 0) {
            feeds = feeds.limit(maxFeedsPerRun);
        }
        return feeds.toList();
    }

    Elements limitArticles(Elements items) {
        if (maxArticlesPerFeed <= 0 || items.size() <= maxArticlesPerFeed) {
            return items;
        }
        Elements limited = new Elements();
        items.stream().limit(maxArticlesPerFeed).forEach(limited::add);
        return limited;
    }

    int processItems(RssFeedConfig.FeedSource feed, Elements items) {
        return processItemsWithStats(feed, items).savedCount();
    }

    CollectionBatchStats processItemsWithStats(RssFeedConfig.FeedSource feed, Elements items) {
        try {
            List<String> urls = items.stream()
                    .map(item -> item.select("link").text().trim())
                    .filter(url -> !url.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
            Set<String> existingUrls = urls.isEmpty()
                    ? Set.of()
                    : articleRepository.findExistingUrls(urls);

            // feed.sourceName()은 하드코딩된 RSS 피드 설정값이므로 null/empty 가능성 없음
            Source source = sourceService.getOrCreateSource(feed.sourceName(), null);
            if (source == null) {
                return CollectionBatchStats.from(items.size(), items.size(), 0, 0, List.of());
            }

            List<Article> toSave = new ArrayList<>();
            List<Instant> validPublishedAtValues = new ArrayList<>();
            Set<String> seenUrls = new HashSet<>();
            int invalidCount = 0;
            int duplicateCount = 0;

            for (Element item : items) {
                String url = item.select("link").text().trim();
                String title = item.select("title").text().trim();
                String pubDateStr = item.select("pubDate").text().trim();
                // RDF 피드(Deutsche Welle 등)는 <pubDate> 대신 <dc:date> 사용
                if (pubDateStr.isBlank()) {
                    pubDateStr = item.select("dc|date").text().trim();
                }

                // 필수 필드 유효성 검사
                if (url.isBlank() || title.isBlank() || pubDateStr.isBlank()) {
                    invalidCount++;
                    continue;
                }

                // 날짜 파싱
                ParsedRssDate publishedAt = parseRssDate(pubDateStr);
                if (publishedAt == null) {
                    invalidCount++;
                    continue;
                }
                validPublishedAtValues.add(publishedAt.instant());

                // 중복 체크
                if (!seenUrls.add(url) || existingUrls.contains(url)) {
                    duplicateCount++;
                    continue;
                }

                String description = item.select("description").text().trim();
                String author = extractAuthor(item);
                String urlToImage = extractImage(item);

                Article article = Article.createRssArticle(
                        source, author, title, description, null,
                        url, urlToImage, publishedAt.localDateTime(),
                        feed.country(), feed.category(), feed.language()
                );
                toSave.add(article);
            }

            if (!toSave.isEmpty()) {
                articleRepository.saveAll(toSave);
            }

            CollectionBatchStats stats = CollectionBatchStats.from(
                    items.size(), invalidCount, duplicateCount, toSave.size(), validPublishedAtValues);
            log.info("[수집 통계] provider=rss source={} country={} language={} category={} response={} invalid={} duplicate={} saved={} oldestPublishedAt={} latestPublishedAt={} freshnessSeconds={}",
                    feed.sourceName(), feed.country(), feed.language(), feed.category(), stats.receivedCount(),
                    stats.invalidCount(), stats.duplicateCount(), stats.savedCount(), stats.oldestPublishedAt(),
                    stats.latestPublishedAt(), stats.freshnessSeconds(Instant.now()));

            return stats;

        } catch (Exception e) {
            log.error("[RSS 수집 처리 오류] {} 처리 중 오류 발생", feed.sourceName(), e);
            return CollectionBatchStats.from(items.size(), items.size(), 0, 0, List.of());
        }
    }

    private ParsedRssDate parseRssDate(String pubDateStr) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                ZonedDateTime parsed = ZonedDateTime.parse(pubDateStr, formatter);
                return new ParsedRssDate(parsed.toLocalDateTime(), parsed.toInstant());
            } catch (DateTimeParseException ignored) {
            }
        }
        log.warn("[RSS 날짜 파싱 실패] pubDate: {}", pubDateStr);
        return null;
    }

    private record ParsedRssDate(LocalDateTime localDateTime, Instant instant) {
    }

    private String extractAuthor(Element item) {
        String author = item.select("dc|creator").text().trim();
        if (author.isBlank()) author = item.select("author").text().trim();
        return author;
    }

    private String extractImage(Element item) {
        // <media:thumbnail url="..."> 또는 <media:content url="...">
        Element mediaThumbnail = item.selectFirst("media|thumbnail");
        if (mediaThumbnail != null) return mediaThumbnail.attr("url");

        Element mediaContent = item.selectFirst("media|content");
        if (mediaContent != null) return mediaContent.attr("url");

        // <enclosure url="..." type="image/...">
        Element enclosure = item.selectFirst("enclosure");
        if (enclosure != null && enclosure.attr("type").startsWith("image")) {
            return enclosure.attr("url");
        }
        return "";
    }
}
