package com.example.globalTimes_be.domain.chat.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.chat.dto.ChatHistoryListResDTO;
import com.example.globalTimes_be.domain.chat.dto.ChatHistoryResDTO;
import com.example.globalTimes_be.domain.chat.entity.ChatHistory;
import com.example.globalTimes_be.domain.chat.repository.ChatHistoryRepository;
import com.example.globalTimes_be.domain.user.entity.User;
import com.example.globalTimes_be.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;

    // GPT 질의 완료 시 저장 (AiSseService에서 호출)
    @Transactional
    public void save(Long userId, Long articleId, String question, String answer) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + userId));
            Article article = articleRepository.findById(articleId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 기사: " + articleId));

            chatHistoryRepository.save(ChatHistory.create(user, article, question, answer));
            log.info("[ChatHistory] 저장 완료 - userId: {}, articleId: {}", userId, articleId);
        } catch (Exception e) {
            log.error("[ChatHistory] 저장 실패 - userId: {}, articleId: {}, error: {}", userId, articleId, e.getMessage());
        }
    }

    // 팝업 목록용: 사용자의 기사별 마지막 대화 미리보기
    @Transactional(readOnly = true)
    public List<ChatHistoryListResDTO> getChatList(Long userId) {
        List<ChatHistory> all = chatHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // 기사별로 가장 최신 채팅 1건만 추출 (이미 최신순 정렬이므로 첫 번째가 최신)
        Map<Long, ChatHistory> latestPerArticle = new LinkedHashMap<>();
        for (ChatHistory chat : all) {
            latestPerArticle.putIfAbsent(chat.getArticle().getId(), chat);
        }

        return latestPerArticle.values().stream()
                .map(ChatHistoryListResDTO::from)
                .collect(Collectors.toList());
    }

    // 상세 페이지용: 특정 기사의 전체 대화 내역
    @Transactional(readOnly = true)
    public List<ChatHistoryResDTO> getChatsByArticle(Long userId, Long articleId) {
        return chatHistoryRepository
                .findByUserIdAndArticleIdOrderByCreatedAtAsc(userId, articleId)
                .stream()
                .map(ChatHistoryResDTO::from)
                .collect(Collectors.toList());
    }
}
