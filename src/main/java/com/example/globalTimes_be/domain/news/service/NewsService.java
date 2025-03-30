package com.example.globalTimes_be.domain.news.service;

import com.example.globalTimes_be.domain.news.dto.response.NewsDetailDTO;
import com.example.globalTimes_be.domain.news.dto.response.NewsResDTO;
import com.example.globalTimes_be.domain.news.dto.response.RecentNewsDTO;
import com.example.globalTimes_be.domain.news.entity.NewsEntity;
import com.example.globalTimes_be.domain.news.exception.NewsErrorStatus;
import com.example.globalTimes_be.domain.news.repository.NewsRepository;
import com.example.globalTimes_be.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class NewsService {
    private final NewsRepository newsRepository;

    //아이디를 받으면 DB에서 조회 후 값 반환
    public NewsResDTO getNewsDetail(Long id) {
        //뉴스 정보 가져옴
        NewsEntity newsEntity = newsRepository.findById(id)
                .orElseThrow(() -> new BaseException(NewsErrorStatus._EMPTY_NEWS_DATA.getResponse()));

        //상세 뉴스 정보를 DTO로 담음
        NewsDetailDTO newsDetailDTO = NewsDetailDTO.builder()
                .sourceName(newsEntity.getSourceName())
                .author(newsEntity.getAuthor())
                .title(newsEntity.getTitle())
                .url(newsEntity.getUrl())
                .urlToImage(newsEntity.getUrlToImage())
                .publishedAt(newsEntity.getPublishedAt())
                .build();

        //0번째 페이지, 한 페이지에 20개
        Pageable pageable = PageRequest.of(0, 20);

        //최신 뉴스 정보를 20개 가져옴
        List<NewsEntity> newsEntityList = newsRepository.findAll(pageable).getContent();

        //최신 뉴스 정보를 DTO에 넣음
        List<RecentNewsDTO> recentNewsDTOList = newsEntityList.stream()
            .map(news -> {
                return RecentNewsDTO.builder()
                        .id(news.getId())
                        .sourceName(news.getSourceName())
                        .title(news.getTitle())
                        .urlToImage(news.getUrlToImage())
                        .publishedAt(news.getPublishedAt())
                        .build();
            })
            .toList();

        //뉴스 응답 DTO에 상세 뉴스 정보 DTO와 최신 뉴스 정보 DTO를 넣어서 반환
        return NewsResDTO.builder()
                .newsDetail(newsDetailDTO)
                .recentNewsList(recentNewsDTOList)
                .build();
    }

    //DB에 뉴스 기사가 없으면 원본 뉴스 사이트에서 크롤링해옴
    @Transactional
    public String getArticleCrawledContent(Long id) {
        //뉴스 정보 가져옴
        NewsEntity newsEntity = newsRepository.findById(id)
                .orElseThrow(() -> new BaseException(NewsErrorStatus._EMPTY_NEWS_DATA.getResponse()));

        System.out.println("newsEntity: " + newsEntity.getUrl());

        String crawledContent = newsEntity.getCrawledContent();

        //content가 null이면 크롤링 해오고 db에 저장
        if (crawledContent == null) {

            // 크롤러로 기사 원문 가져옴
            crawledContent = getCrawlerUrl(newsEntity.getUrl());

            // 크롤링 실패시 에러처리
            if (crawledContent == null) {
                throw new BaseException(NewsErrorStatus._CRAWLER_ERROR.getResponse());
            }

            //DB에 저장
            newsEntity.setCrawledContent(crawledContent);
            newsRepository.save(newsEntity);
        }

        return crawledContent;
    }

    //크롤링 하는 메서드
    private String getCrawlerUrl(String crawlerUrl) {
        try {
            // URL에서 HTML 문서 가져오기
            Document doc = Jsoup.connect(crawlerUrl).get();

            // 모든 <p> 태그 가져오기
            Elements paragraphs = doc.select("p");

            // 텍스트만 추출해서 하나의 문자열로 반환 (줄바꿈 포함)
            return paragraphs.stream()
                    .map(Element::text)
                    .reduce((p1, p2) -> p1 + "\n" + p2) // 문장마다 줄바꿈 추가
                    .orElse(null);

        } catch (IOException e) {
            log.error("크롤링에 실패했습니다. \n{}", e.getMessage());
            return null;
        }
    }
}
