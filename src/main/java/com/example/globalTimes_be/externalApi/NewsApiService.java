package com.example.globalTimes_be.externalApi;

import com.example.globalTimes_be.domain.Article;
import com.example.globalTimes_be.domain.Source;
import com.example.globalTimes_be.repository.ArticleRepository;
import com.example.globalTimes_be.repository.SourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

// 전적인 API 호출 ( 외부 ) 처리 및 관리하는 서비스 레이어
@Service
public class NewsApiService {

    private final RestTemplate restTemplate;
    private final ArticleRepository articleRepository;
    private final SourceRepository sourceRepository;

    @Autowired
    public NewsApiService(RestTemplate restTemplate, ArticleRepository articleRepository, SourceRepository sourceRepository) {
        this.restTemplate = restTemplate;
        this.articleRepository = articleRepository;
        this.sourceRepository = sourceRepository;
    }

    public void fetchAndSaveNews() {
        // 우선 테스트는 10개만 ( 1페이지 설정만 해보자 )
        String apiUrl = "https://newsapi.org/v2/top-headlines?country=us&apiKey=";

        // ResponseDto : API 응답 최상위 Dto
        NewsApiResponseDto response = restTemplate.getForObject(apiUrl, NewsApiResponseDto.class);

        if (response != null && response.getArticles() != null) {
            // 응답 자체가 유효하다면, 각각의 Article에 대해

            for (NewsApiArticleDto articleDto : response.getArticles()) {
                // DTO -> Entity 변환
                Article article = mapDtoToEntity(articleDto);

                Source source = article.getSource();
                if (source != null) {

                    Optional<Source> existingSource = sourceRepository.findBySourceName(source.getSourceName());
                    if (existingSource.isPresent()) {
                        // 존재하면 그대로 쓰고
                        article.updateSource(existingSource.get());
                    } else {
                        sourceRepository.save(source);
                    }
                }

                // Article 저장 ( Source 먼저 저장해야 ( FK -> 영속성 관련 )
                articleRepository.save(article);
            }
        }
    }

    private Article mapDtoToEntity(NewsApiArticleDto articleDto) {
        // NewsApiSourceDto를 Source 객체로
        Source source = mapSourceDtoToEntity(articleDto.getSource());

        return Article.createArticle(
                source,
                articleDto.getAuthor(),
                articleDto.getTitle(),
                articleDto.getDescription(),
                articleDto.getContent(),
                articleDto.getUrl(),
                articleDto.getUrlToImage(),
                articleDto.getPublishedAt()
        );
    }

    private Source mapSourceDtoToEntity(NewsApiSourceDto sourceDto) {
        // sourceDto가 null일 수 있으므로 null 체크
        if (sourceDto == null) {
            return null;
        }

        return Source.createSource(sourceDto.getId(), sourceDto.getName());
    }

}
