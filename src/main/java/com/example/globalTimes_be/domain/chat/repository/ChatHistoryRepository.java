package com.example.globalTimes_be.domain.chat.repository;

import com.example.globalTimes_be.domain.chat.entity.ChatHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    @Query(value = """
            SELECT ranked.article_id AS articleId,
                   article.title AS articleTitle,
                   article.url_to_image AS thumbnailUrl,
                   ranked.question AS lastQuestion,
                   ranked.answer AS lastAnswer,
                   ranked.created_at AS lastChatAt
            FROM (
                SELECT chat.article_id,
                       chat.chat_id,
                       chat.question,
                       chat.answer,
                       chat.created_at,
                       ROW_NUMBER() OVER (
                           PARTITION BY chat.article_id
                           ORDER BY chat.created_at DESC, chat.chat_id DESC
                       ) AS rn
                FROM chat_history chat
                WHERE chat.user_id = :userId
            ) ranked
            JOIN article ON article.article_id = ranked.article_id
            WHERE ranked.rn = 1
            ORDER BY ranked.created_at DESC, ranked.chat_id DESC
            """, nativeQuery = true)
    List<LatestChatHistoryProjection> findLatestPerArticleByUserId(@Param("userId") Long userId);

    // 특정 기사의 사용자 히스토리 (오래된순) - 상세 페이지 대화 흐름용
    List<ChatHistory> findByUserIdAndArticleIdOrderByCreatedAtAsc(Long userId, Long articleId);

    // 슬라이딩 윈도우 컨텍스트용: 최근 N건만 최신순으로 조회
    List<ChatHistory> findByUserIdAndArticleIdOrderByCreatedAtDesc(Long userId, Long articleId, Pageable pageable);
}
