package com.example.globalTimes_be.domain.trend.service;

import com.example.globalTimes_be.domain.trend.exception.TrendErrorStatus;
import com.example.globalTimes_be.global.crawler.ArticleCrawler;
import com.example.globalTimes_be.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class TrendCrawledService {

    private final ArticleCrawler articleCrawler;

    //url 본문 기사 크롤링
    public String getArticleCrawledContent(String url){
        return articleCrawler.crawlParagraphs(url)
                .orElseThrow(() -> new BaseException(TrendErrorStatus._CRAWLER_ERROR.getResponse()));
    }
}
