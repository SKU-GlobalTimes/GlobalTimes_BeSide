package com.example.globalTimes_be.domain.detail.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.detail.dto.response.PerspectiveArticleDTO;
import com.example.globalTimes_be.domain.detail.dto.response.PerspectivesResDTO;
import com.example.globalTimes_be.domain.detail.exception.DetailErrorStatus;
import com.example.globalTimes_be.domain.detail.util.KeywordExtractor;
import com.example.globalTimes_be.domain.search.service.TranslationService;
import com.example.globalTimes_be.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class PerspectivesService {

    private final ArticleRepository articleRepository;
    private final TranslationService translationService;

    private static final int MAX_ARTICLES_PER_COUNTRY = 3;

    @Transactional(readOnly = true)
    public PerspectivesResDTO getPerspectives(Long articleId) {
        Article base = articleRepository.findById(articleId)
                .orElseThrow(() -> new BaseException(DetailErrorStatus._EMPTY_NEWS_DATA.getResponse()));

        String plainKeyword = KeywordExtractor.extractPlain(base.getTitle());
        String booleanKeyword = KeywordExtractor.extract(base.getTitle());

        log.info("[Perspectives] 기사 id={} 키워드 추출: '{}'", articleId, plainKeyword);

        // 영어가 아닌 기사는 제목을 영어로 번역해 영어권 기사도 탐색
        String searchKeyword = booleanKeyword;
        if (!"en".equals(base.getLanguage())) {
            try {
                String translated = translationService.translateToEnglish(plainKeyword);
                searchKeyword = KeywordExtractor.extract(translated);
                log.info("[Perspectives] 번역된 키워드: '{}'", translated);
            } catch (Exception e) {
                log.warn("[Perspectives] 번역 실패, 원문 키워드로 탐색: {}", e.getMessage());
            }
        }

        List<Article> results = articleRepository.findPerspectives(articleId, searchKeyword);

        // 원문 키워드로도 추가 탐색 (번역 키워드와 다를 경우)
        if (!searchKeyword.equals(booleanKeyword) && !booleanKeyword.isBlank()) {
            List<Article> originalResults = articleRepository.findPerspectives(articleId, booleanKeyword);
            // 중복 제거 후 병합
            List<Long> existingIds = results.stream().map(Article::getId).toList();
            originalResults.stream()
                    .filter(a -> !existingIds.contains(a.getId()))
                    .forEach(results::add);
        }

        // 국가별 그룹핑 (자기 자신 국가 포함, 국가당 최대 3개)
        Map<String, List<PerspectiveArticleDTO>> perspectives = results.stream()
                .collect(Collectors.groupingBy(
                        Article::getCountry,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .limit(MAX_ARTICLES_PER_COUNTRY)
                                        .map(PerspectiveArticleDTO::fromEntity)
                                        .toList()
                        )
                ));

        int totalArticles = perspectives.values().stream()
                .mapToInt(List::size)
                .sum();

        log.info("[Perspectives] 기사 id={} | 키워드='{}' | {}개 국가, {}개 기사 반환",
                articleId, plainKeyword, perspectives.size(), totalArticles);

        return PerspectivesResDTO.builder()
                .keyword(plainKeyword)
                .perspectives(perspectives)
                .countriesFound(perspectives.size())
                .totalArticles(totalArticles)
                .build();
    }
}
