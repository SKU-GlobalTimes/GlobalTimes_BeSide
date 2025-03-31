package com.example.globalTimes_be.domain.article.repository;

import com.example.globalTimes_be.domain.article.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    boolean existsByUrl(String url); // 기사 중복 저장 방지

    // 최신순
    Page<Article> findAllByOrderByPublishedAtDesc(Pageable pageable);

    // 조회수 순 ( 랜딩페이지 Hot News 기반 )
    Page<Article> findAllByOrderByViewCountDesc(Pageable pageable);
}
