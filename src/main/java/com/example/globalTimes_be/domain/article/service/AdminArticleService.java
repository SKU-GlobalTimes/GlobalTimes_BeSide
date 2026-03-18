package com.example.globalTimes_be.domain.article.service;

import com.example.globalTimes_be.domain.article.dto.AdminArticleSearchDto;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminArticleService {

    private final ArticleRepository articleRepository;

    @Transactional(readOnly = true)
    public Page<AdminArticleSearchDto> searchArticles(
            String country,
            String category,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return articleRepository.findByFilters(country, category, from, to, pageable)
                .map(AdminArticleSearchDto::fromEntity);
    }
}
