package com.example.globalTimes_be.domain.article.repository;

import com.example.globalTimes_be.domain.article.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import java.util.List;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    boolean existsByUrl(String url); // 기사 중복 저장 방지

    // 최신순
    Page<Article> findAllByOrderByPublishedAtDesc(Pageable pageable);

    // 조회수 순 ( 랜딩페이지 Hot News 기반 )
    Page<Article> findAllByOrderByViewCountDesc(Pageable pageable);

    @Query("SELECT a.url FROM Article a WHERE a.url IN :urls")
    Set<String> findExistingUrls(@Param("urls") List<String> urls);

    // 특정 id를 제외한 최신기사 20개 조회
    List<Article> findTop20ByIdNotOrderByPublishedAtDesc(Long id);

    // 번역 전 단어와 번역 후 단어를 통해 title과 description에서 조회후 최신순으로 정렬
    @Query("SELECT a FROM Article AS a " +
            "WHERE LOWER(a.title) LIKE LOWER(CONCAT('%', :text1, '%')) "+
            "   OR LOWER(a.title) LIKE LOWER(CONCAT('%', :text2, '%')) "+
            "   OR LOWER(a.description) LIKE LOWER(CONCAT('%', :text1, '%')) "+
            "   OR LOWER(a.description) LIKE LOWER(CONCAT('%', :text2, '%')) "+
            "ORDER BY a.publishedAt DESC")
    List<Article> searchByDescriptionOrTitle(@Param("text1") String text,
                                             @Param("text2") String translatedText,
                                             Pageable pageable);

    // 현재 시간 기준 이틀 이내인지 확인 ( 기준의 Hot News 요청을 위한 쿼리 메소드 )
    // publishedAt 으로 찾고 After -> 이후에 내림차순으로.
    Page<Article> findByPublishedAtAfterOrderByViewCountDesc(LocalDateTime from, Pageable pageable);
}
