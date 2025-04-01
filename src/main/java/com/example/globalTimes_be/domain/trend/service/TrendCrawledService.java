package com.example.globalTimes_be.domain.trend.service;

import com.example.globalTimes_be.domain.trend.exception.TrendErrorStatus;
import com.example.globalTimes_be.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
@Service
public class TrendCrawledService {

    //url 본문 기사 크롤링
    public String getArticleCrawledContent(String url){
        try {
            // URL에서 HTML 문서 가져오기
            Document doc = Jsoup.connect(url).get();

            // 모든 <p> 태그 가져오기
            Elements paragraphs = doc.select("p");

            // 텍스트만 추출해서 하나의 문자열로 반환 (줄바꿈 포함)
            return paragraphs.stream()
                    .map(Element::text)
                    .reduce((p1, p2) -> p1 + "\n" + p2) // 문장마다 줄바꿈 추가
                    .orElse(null);

        } catch (IOException e) {
            log.error("크롤링에 실패했습니다. \n{}", e.getMessage());
            throw new BaseException(TrendErrorStatus._CRAWLER_ERROR.getResponse());
        }
    }
}
