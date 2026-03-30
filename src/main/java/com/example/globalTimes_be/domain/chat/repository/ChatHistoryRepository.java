package com.example.globalTimes_be.domain.chat.repository;

import com.example.globalTimes_be.domain.chat.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    // 사용자의 전체 히스토리 (최신순) - 팝업 목록용
    List<ChatHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 특정 기사의 사용자 히스토리 (오래된순) - 상세 페이지 대화 흐름용
    List<ChatHistory> findByUserIdAndArticleIdOrderByCreatedAtAsc(Long userId, Long articleId);
}
