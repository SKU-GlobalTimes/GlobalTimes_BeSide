package com.example.globalTimes_be.domain.trend.scheduler;

import com.example.globalTimes_be.domain.trend.dto.resonse.TrendDTO;
import com.example.globalTimes_be.domain.trend.service.TrendService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Slf4j
@RequiredArgsConstructor
@Component
public class TrendScheduler {
    private final RestTemplate restTemplate;  // timeout 설정된 빈 주입
    private final TrendService trendService;

    @Value("${news-fetch.trend-enabled:${news-fetch.enabled:false}}")
    private boolean fetchEnabled;

    @Value("${news-fetch.trend-countries:KR,AU,AT,BR,CA,CO,DK,EG,FR,DE,GR,HK,IN,ID,IT,JP,MY,MX,NL,RU,SG,TW,TR,GB,US,ES}")
    private String fetchCountries;

    @Value("${news-fetch.trend-max-items-per-country:6}")
    private int maxItemsPerCountry;

    @PostConstruct
    public void init() {
        if (!fetchEnabled) {
            log.info("[트렌드 초기화] Trend 수집 비활성화 → 건너뜀");
            return;
        }
        saveTrendApi();
    }

    @Scheduled(cron = "0 0 */1 * * *")
    public void saveTrendApi(){
        if (!fetchEnabled) return;
        int totalKeywords = 0;
        int successCount = 0;

        for (String countryCode : selectedCountries()){
            List<TrendDTO> trendDTOS = createTrendDTOS(countryCode);

            if(trendDTOS == null || trendDTOS.isEmpty()){
                continue;
            }

            trendService.replaceTrendKeywords(countryCode, trendDTOS);

            totalKeywords += trendDTOS.size();
            successCount++;
        }

        log.info("[트렌드 수집] 완료 | {}개국, 총 {}개 키워드 저장", successCount, totalKeywords);
    }

    // 나라별 실검 리스트 생성
    private List<TrendDTO> createTrendDTOS(String countryCode){
        //item최대 저장 갯수 조절용 변수
        int count = 0;
        
        //trend dto 리스트 생성
        List<TrendDTO> trendDTOS = new ArrayList<>();

        //요청보낼 url
        String apiUrl = "https://trends.google.co.kr/trending/rss?geo=" + countryCode;

        //get요청 보내기
        String xmlResponse = null;

        try {
            xmlResponse = restTemplate.getForObject(apiUrl, String.class);
        } catch (RestClientException e) {
            log.error("url get요청 에러: {}", e.getMessage());
            return null;
        }

        //파싱 실패
        if (xmlResponse == null) {
            log.error("XML코드 파싱 실패");
            return null;
        }

        // XML 파싱
        Document doc = Jsoup.parse(xmlResponse, "", org.jsoup.parser.Parser.xmlParser());

        // 모든 <item> 태그 가져오기
        Elements items = doc.select("item");

        //반복문으로 items안에 있는 데이터 가져오기
        for (Element item : items) {
            // 설정된 국가별 최대 개수만 저장한다. 0 이하면 기존처럼 전체 item을 허용한다.
            if(maxItemsPerCountry > 0 && count >= maxItemsPerCountry){
                break;
            }
            
            //키워드 가져오기
            String keyword = item.select("title").text();
            //키워드가 없으면 패스
            if (keyword.isEmpty()) {
                continue;
            }

            // 첫 번째 <ht:news_item> 찾기
            Element firstNewsItem = item.selectFirst("ht|news_item");
            //firstNewsItem이 없으면 패스
            if (firstNewsItem == null) {
                continue;
            }

            //제목 가져오기
            String title = firstNewsItem.select("ht|news_item_title").text();
            //제목이 없으면 패스
            if (title.isEmpty()) {
                continue;
            }

            //원본 url가져오기
            String url = firstNewsItem.select("ht|news_item_url").text();
            //url이 없으면 패스
            if (url.isEmpty()) {
                continue;
            }

            //언론사명 가져오기
            String sourceName = firstNewsItem.select("ht|news_item_source").text();
            //언론사명이 없으면 패스
            if (sourceName.isEmpty()) {
                continue;
            }

            //이미지 url가져오기
            String urlToImage = firstNewsItem.select("ht|news_item_picture").text();
            //이미지 url 없으면 패스
            if (urlToImage.isEmpty()) {
                continue;
            }

            //실검 dto 생성
            TrendDTO trendDTO = TrendDTO.builder()
                    .keyword(keyword)
                    .title(title)
                    .url(url)
                    .sourceName(sourceName)
                    .urlToImage(urlToImage)
                    .build();

            //리스트에 추가
            trendDTOS.add(trendDTO);
            
            count++;
        }

        return trendDTOS;
    }

    List<String> selectedCountries() {
        if (fetchCountries == null || fetchCountries.isBlank()) {
            return List.of();
        }
        return Arrays.stream(fetchCountries.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
