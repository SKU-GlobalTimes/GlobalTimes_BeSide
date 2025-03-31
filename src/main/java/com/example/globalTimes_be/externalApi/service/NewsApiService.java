package com.example.globalTimes_be.externalApi.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.source.entity.Source;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.source.repository.SourceRepository;
import com.example.globalTimes_be.externalApi.dto.NewsApiArticleDto;
import com.example.globalTimes_be.externalApi.dto.NewsApiResponseDto;
import com.example.globalTimes_be.externalApi.dto.NewsApiSourceDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Transactional
    public void fetchAndSaveNews(int targetSize) {

        int page = 0;
        int pageSize = 20;  // 한 페이지당 최대 100개 ( 단일 요청 ) : 우선 테스트용 ( default 는 20 )
        int savedCount = 0;

        String apiUrl = "https://newsapi.org/v2/top-headlines?country=us&apiKey={key}&pageSize=" + pageSize;
        while(savedCount < targetSize){

            String finalApiUrl = apiUrl + "&page=" + page;

            // 목표로 하는 데이터 수 적재 실패 시 while loop - 추가적으로 요청해서 targetSize에 도달할 때 까지 반복
            NewsApiResponseDto response = restTemplate.getForObject(finalApiUrl, NewsApiResponseDto.class);
            if (response == null || response.getArticles() == null || response.getArticles().isEmpty()) {
                break;
                // null : 리스트 자체가 없는 경우
                // isEmpty : 존재하지만 내부 요소가 없는 경우 -> 두가지 경우 전부 판단 ( NullPointer )
            }

            for (NewsApiArticleDto articleDto : response.getArticles()) {
                // DTO -> Entity 변환

                if(isInvalid(articleDto)){
                    continue; // 각 항목 null 체크
                }

                // 아니라면 엔티티로 변환
                Article article = mapDtoToEntity(articleDto);

                // 기사 중복 체크 ( 이미 저장된 것이라면 제외 )
                if (articleRepository.existsByUrl(article.getUrl())) {
                    continue;
                }

                Source source = article.getSource();

                if (source != null) {
                    // null Pointer Ex 방지
                    if (source.getSourceName() == null || source.getSourceName().isEmpty()) {
                        continue;
                    }
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
                savedCount++; // 이렇게하면 이거 무조건 근데, 성능 문제 생김. ( 개별 쿼리 형태 )
                // 루프 개별적으로 순회 하는거 이거 전에 플젝에서도 수정함. 잊지말기 (list 형태로 saveAll 추후 반드시 수정하고. )

                if (savedCount >= targetSize) {
                    return;
                }
            }
            page++;
        }
    }

    private boolean isInvalid(NewsApiArticleDto dto){
        // 하나라도 null 인 케이스들을 방지
        return dto.getAuthor() == null ||
                dto.getTitle() == null ||
                dto.getDescription() == null ||
                dto.getContent() == null ||
                dto.getUrl() == null ||
                dto.getUrlToImage() == null ||
                dto.getSource() == null ||
                dto.getSource().getName() == null ||
                dto.getSource().getName().isEmpty();
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
