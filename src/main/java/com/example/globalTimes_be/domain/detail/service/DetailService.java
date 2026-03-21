package com.example.globalTimes_be.domain.detail.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.detail.dto.response.DetailResDTO;
import com.example.globalTimes_be.domain.detail.dto.response.DetailResponseDTO;
import com.example.globalTimes_be.domain.detail.dto.response.RecentArticleDTO;
import com.example.globalTimes_be.domain.detail.exception.DetailErrorStatus;
import com.example.globalTimes_be.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class DetailService {
    private final ArticleRepository articleRepository;

    public DetailResDTO getNewsDetail(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new BaseException(DetailErrorStatus._EMPTY_NEWS_DATA.getResponse()));

        article.increaseViewCount();
        articleRepository.save(article);

        DetailResponseDTO detailResponseDTO = DetailResponseDTO.builder()
                .sourceName(article.getSource().getSourceName())
                .author(article.getAuthor())
                .title(article.getTitle())
                .viewCount(article.getViewCount())
                .url(article.getUrl())
                .urlToImage(article.getUrlToImage())
                .publishedAt(article.getPublishedAt())
                .build();

        List<Article> articleList = articleRepository.findTop20ByIdNotOrderByPublishedAtDesc(id);

        List<RecentArticleDTO> recentArticleDTOList = articleList.stream()
                .map(news -> RecentArticleDTO.builder()
                        .id(news.getId())
                        .sourceName(news.getSource().getSourceName())
                        .title(news.getTitle())
                        .urlToImage(news.getUrlToImage())
                        .publishedAt(news.getPublishedAt())
                        .build())
                .toList();

        return DetailResDTO.builder()
                .newsDetail(detailResponseDTO)
                .recentNewsList(recentArticleDTOList)
                .build();
    }

    public String getArticleContent(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new BaseException(DetailErrorStatus._EMPTY_NEWS_DATA.getResponse()));

        return article.getContent();
    }

    // @Transactional 제거: 크롤링(네트워크 I/O)을 트랜잭션 밖에서 실행해 DB 커넥션 점유 시간 최소화
    public String getArticleCrawledContent(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new BaseException(DetailErrorStatus._EMPTY_NEWS_DATA.getResponse()));

        String crawledContent = article.getCrawledContent();

        if (crawledContent != null) {
            return crawledContent;
        }

        crawledContent = getCrawlerUrl(article.getUrl());

        if (crawledContent == null) {
            throw new BaseException(DetailErrorStatus._CRAWLER_ERROR.getResponse());
        }

        article.updateCrawledContent(crawledContent);
        articleRepository.save(article);

        return crawledContent;
    }

    private String getCrawlerUrl(String crawlerUrl) {
        try {
            Document doc = Jsoup.connect(crawlerUrl).get();
            Elements paragraphs = doc.select("p");

            return paragraphs.stream()
                    .map(Element::text)
                    .reduce((p1, p2) -> p1 + "\n" + p2)
                    .orElse(null);

        } catch (IOException e) {
            log.error("크롤링에 실패했습니다. \n{}", e.getMessage());
            return null;
        }
    }
}
