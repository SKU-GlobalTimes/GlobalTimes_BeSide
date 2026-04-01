package com.example.globalTimes_be.domain.scrap.repository;

import com.example.globalTimes_be.domain.scrap.entity.Scrap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScrapRepository extends JpaRepository<Scrap, Long> {

    // 스크랩 여부 확인
    Optional<Scrap> findByUserIdAndArticleId(Long userId, Long articleId);

    // 사용자 스크랩 목록 (최신순)
    List<Scrap> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 스크랩 존재 여부 (boolean)
    boolean existsByUserIdAndArticleId(Long userId, Long articleId);
}
