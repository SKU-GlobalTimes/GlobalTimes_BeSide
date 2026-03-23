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

    // Full-Text Search: 원문 검색어 + 영어 번역 검색어를 MATCH AGAINST로 탐색 (최신순, 최대 100개)
    // LIKE 대비 FULLTEXT 인덱스를 활용해 Full Table Scan 없이 빠른 탐색 가능
    // 한계: 검색어가 영어로 번역되어 탐색하므로 영어 기사 위주 탐색 (비영어 기사는 해당 언어 검색 시만 탐색)
    @Query(value =
            "SELECT * FROM article " +
            "WHERE MATCH(title, description) AGAINST(:text1 IN BOOLEAN MODE) " +
            "   OR MATCH(title, description) AGAINST(:text2 IN BOOLEAN MODE) " +
            "ORDER BY published_at DESC " +
            "LIMIT 100",
            nativeQuery = true)
    List<Article> searchByDescriptionOrTitle(@Param("text1") String text,
                                             @Param("text2") String translatedText);

    // 현재 시간 기준 이틀 이내인지 확인 ( 기준의 Hot News 요청을 위한 쿼리 메소드 )
    // publishedAt 으로 찾고 After -> 이후에 내림차순으로.
    Page<Article> findByPublishedAtAfterOrderByViewCountDesc(LocalDateTime from, Pageable pageable);

    // 관리자용: country/category/publishedAt 범위 필터 + 페이징
    @Query("SELECT a FROM Article a " +
            "WHERE (:country IS NULL OR a.country = :country) " +
            "AND (:category IS NULL OR a.category = :category) " +
            "AND (:from IS NULL OR a.publishedAt >= :from) " +
            "AND (:to IS NULL OR a.publishedAt <= :to) " +
            "ORDER BY a.publishedAt DESC")
    Page<Article> findByFilters(
            @Param("country") String country,
            @Param("category") String category,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}
