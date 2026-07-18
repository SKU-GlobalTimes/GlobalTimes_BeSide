package com.example.globalTimes_be.domain.scrap.repository;

import com.example.globalTimes_be.domain.scrap.entity.Scrap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScrapRepository extends JpaRepository<Scrap, Long> {

    // 스크랩 여부 확인
    Optional<Scrap> findByUserIdAndArticleId(Long userId, Long articleId);

    @Query("""
            SELECT article.id AS articleId,
                   article.title AS title,
                   source.sourceName AS sourceName,
                   article.urlToImage AS urlToImage,
                   article.description AS description,
                   article.publishedAt AS publishedAt,
                   scrap.createdAt AS scrappedAt
            FROM Scrap scrap
            JOIN scrap.article article
            LEFT JOIN article.source source
            WHERE scrap.user.id = :userId
            ORDER BY scrap.createdAt DESC, scrap.id DESC
            """)
    List<ScrapListProjection> findListByUserId(@Param("userId") Long userId);

    // 스크랩 존재 여부 (boolean)
    boolean existsByUserIdAndArticleId(Long userId, Long articleId);
}
