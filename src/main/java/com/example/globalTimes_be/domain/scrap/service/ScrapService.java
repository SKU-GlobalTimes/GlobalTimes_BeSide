package com.example.globalTimes_be.domain.scrap.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.scrap.dto.response.ScrapListResDTO;
import com.example.globalTimes_be.domain.scrap.dto.response.ScrapResDTO;
import com.example.globalTimes_be.domain.scrap.entity.Scrap;
import com.example.globalTimes_be.domain.scrap.exception.ScrapErrorStatus;
import com.example.globalTimes_be.domain.scrap.repository.ScrapRepository;
import com.example.globalTimes_be.domain.user.entity.User;
import com.example.globalTimes_be.domain.user.repository.UserRepository;
import com.example.globalTimes_be.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class ScrapService {

    private final ArticleRepository articleRepository;
    private final ScrapRepository scrapRepository;
    private final UserRepository userRepository;

    // 기존 API 유지 (localStorage 기반 비로그인 호환)
    @Transactional(readOnly = true)
    public List<ScrapResDTO> getScrap(List<Long> articleIds) {
        List<ScrapResDTO> scrapResDTOs = new ArrayList<>();

        for (Long articleId : articleIds) {
            Article article = articleRepository.findById(articleId).orElse(null);
            if (article == null) continue;

            scrapResDTOs.add(ScrapResDTO.builder()
                    .id(article.getId())
                    .sourceName(article.getSource().getSourceName())
                    .title(article.getTitle())
                    .description(article.getDescription())
                    .urlToImage(article.getUrlToImage())
                    .publishedAt(article.getPublishedAt())
                    .build());
        }

        return scrapResDTOs;
    }

    // 스크랩 토글 (추가/취소)
    @Transactional
    public boolean toggle(Long userId, Long articleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ScrapErrorStatus._SCRAP_USER_NOT_FOUND.getResponse()));
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new BaseException(ScrapErrorStatus._SCRAP_ARTICLE_NOT_FOUND.getResponse()));

        Optional<Scrap> existing = scrapRepository.findByUserIdAndArticleId(userId, articleId);

        if (existing.isPresent()) {
            scrapRepository.delete(existing.get());
            log.info("[Scrap] 취소 - userId={}, articleId={}", userId, articleId);
            return false; // 취소됨
        } else {
            scrapRepository.save(Scrap.create(user, article));
            log.info("[Scrap] 추가 - userId={}, articleId={}", userId, articleId);
            return true; // 추가됨
        }
    }

    // 내 스크랩 목록 조회
    @Transactional(readOnly = true)
    public List<ScrapListResDTO> getScrapList(Long userId) {
        return scrapRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ScrapListResDTO::from)
                .collect(Collectors.toList());
    }

    // 특정 기사 스크랩 여부 조회
    @Transactional(readOnly = true)
    public boolean isScrapped(Long userId, Long articleId) {
        return scrapRepository.existsByUserIdAndArticleId(userId, articleId);
    }
}
