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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class TrendScheduler {
    private final RestTemplate restTemplate = new RestTemplate();
    private final TrendService trendService;

    //서버 시작 시 한 번 실행되도록 함
    @PostConstruct
    public void init() {
        saveTrendApi();
    }

    //0시5초, 4시5초, 8시5초, 12시5초, 16시5초, 20시5초마다 갱신
    @Scheduled(cron = "5 0 0,4,8,12,16,20 * * *")
    public void saveTrendApi(){
        //나라 코드 리스트 생성
        List<String> countries = new ArrayList<>
                (Arrays.asList("KR", "AU", "AT", "BR", "CA", "CO", "DK", "EG", "FR", "DE", "GR", "HK", "IN",
                        "ID", "IT", "JP", "MY", "MX", "NL", "RU", "SG", "TW", "TR", "GB", "US", "ES"));

        //나라 코드에 따른 실검 리스트 생성 후 저장
        for (String countryCode : countries){
            //나라 코드에 따른 실검 리스트 생성
            List<TrendDTO> trendDTOS = createTrendDTOS(countryCode);

            //실검 리스트가 비어있으면 패스
            if(trendDTOS == null || trendDTOS.isEmpty()){
                continue;
            }

            //나라 코드를 키로 redis에 저장된 값 파기
            trendService.deleteTrendKeywords(countryCode);

            //나라 코드를 키로 redis에 저장
            trendService.saveTrendKeywords(countryCode, trendDTOS);
        }
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
        String xmlResponse = restTemplate.getForObject(apiUrl, String.class);

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
            // item을 6개 저장했으면 끝냄
            if(count > 5){
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

            //실검 dto 생성 확인
            log.info("실검 키워드 생성 확인: {}", trendDTO.getKeyword());

            //리스트에 추가
            trendDTOS.add(trendDTO);
            
            count++;
        }
        return trendDTOS;
    }
}
