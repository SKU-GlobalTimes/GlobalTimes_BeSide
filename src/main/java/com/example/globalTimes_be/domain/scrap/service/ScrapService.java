package com.example.globalTimes_be.domain.scrap.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.scrap.dto.response.ScrapResDTO;
import com.example.globalTimes_be.domain.scrap.exception.ScrapErrorStatus;
import com.example.globalTimes_be.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class ScrapService {
    private final ArticleRepository articleRepository;

    public List<ScrapResDTO> getScrap(List<Long> articleIds) {
        List<ScrapResDTO> scrapResDTOs = new ArrayList<>();

        for(Long articleId : articleIds) {
            //기사 id로 db에서 정보 가져옴
            Article article = articleRepository.findById(articleId).orElse(null);

            //만약 해당 기사의 정보가 없으면 패스
            if (article == null) {
                continue;
            }

            //응답 DTO 생성
            ScrapResDTO scrapResDTO = ScrapResDTO.builder()
                    .id(article.getId())
                    .sourceName(article.getSource().getSourceName())
                    .title(article.getTitle())
                    .description(article.getDescription())
                    .urlToImage(article.getUrlToImage())
                    .publishedAt(article.getPublishedAt())
                    .build();

            // dto리스트에 추가
            scrapResDTOs.add(scrapResDTO);
        }

        //만약 리스트가 비었다면 에러처리 (프론트 확인용)
        if (scrapResDTOs.isEmpty()) {
            throw new BaseException(ScrapErrorStatus._EMPTY_SCRAP_ARTICLE.getResponse());
        }

        return scrapResDTOs;
    }
}
