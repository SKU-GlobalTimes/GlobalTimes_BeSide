package com.example.globalTimes_be.domain.search.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.search.dto.response.SearchArticleDTO;
import com.example.globalTimes_be.domain.search.dto.response.SearchResDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class SearchArticlesService {
    private final ArticleRepository articleRepository;

    public SearchResDTO getSearchArticles(String text, String translatedText){
        //레포지토리에서 번역 전 단어와 번역 후 단어를 기준으로 조회 (최대 100개)
        Pageable pageable = PageRequest.of(0, 100);
        List<Article> articles = articleRepository.searchByDescriptionOrTitle(text, translatedText, pageable);
        
        // 검색결과에 대한 기사 DTO 리스트 생성
        List<SearchArticleDTO> searchArticleDTOs = new ArrayList<>();
        for (Article article : articles) {
            
            //만약 article이 비어있으면 패스
            if (article == null) {
                continue;
            }
            
            //기사 DTO 생성
            SearchArticleDTO searchArticleDTO = SearchArticleDTO.builder()
                    .id(article.getId())
                    .sourceName(article.getSource().getSourceName())
                    .title(article.getTitle())
                    .description(article.getDescription())
                    .urlToImage(article.getUrlToImage())
                    .publishedAt(article.getPublishedAt())
                    .build();
            
            //기사 DTO 리스트에 삽입
            searchArticleDTOs.add(searchArticleDTO);
        }
        
        //결과 반환
        return SearchResDTO.builder()
                .originalText(text)
                .translatedText(translatedText)
                .searchArticles(searchArticleDTOs)
                .build();
    }
}
