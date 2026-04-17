package com.example.globalTimes_be.domain.chat.service;

import com.example.globalTimes_be.domain.article.entity.Article;
import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.chat.dto.ChatHistoryListResDTO;
import com.example.globalTimes_be.domain.chat.dto.ChatMessagePair;
import com.example.globalTimes_be.global.redis.RedisUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnonymousChatSessionService {

    private static final String KEY_PREFIX = "chat:anon:";
    /** 세션별 기사 ID → 마지막 활동 시각(epoch ms), TTL은 기사 키와 동일 */
    private static final String INDEX_PREFIX = "chat:anon:index:";

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;
    private final ArticleRepository articleRepository;

    @Value("${ai.anonymous-chat.ttl-seconds:604800}")
    private long ttlSeconds;

    public static boolean isValidSessionId(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(raw.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 컨텍스트용: 오래된 순, 최대 windowSize턴.
     */
    public List<ChatMessagePair> getRecentContext(String sessionId, Long articleId, int windowSize) {
        List<ChatMessagePair> all = loadAll(sessionId, articleId);
        if (all.isEmpty() || windowSize <= 0) {
            return all;
        }
        if (all.size() <= windowSize) {
            return all;
        }
        return new ArrayList<>(all.subList(all.size() - windowSize, all.size()));
    }

    /**
     * 상세 채팅 UI용: 저장된 전체 턴 (오래된 순).
     * 기존 Redis만 있고 인덱스가 없을 때 목록 API용 인덱스를 보정한다.
     */
    public List<ChatMessagePair> getFullHistory(String sessionId, Long articleId) {
        List<ChatMessagePair> all = loadAll(sessionId, articleId);
        if (!all.isEmpty()) {
            ensureIndexContains(sessionId.trim(), articleId);
        }
        return all;
    }

    /**
     * 플로팅 히스토리: 세션별로 대화한 기사를 최근 활동순으로, 로그인 {@code GET /api/user/chat-history}와 동일 DTO.
     */
    public List<ChatHistoryListResDTO> getAnonymousChatHistoryList(String sessionId) {
        if (!isValidSessionId(sessionId)) {
            return Collections.emptyList();
        }
        String sid = sessionId.trim();
        Map<Long, Long> index = loadIndexMap(sid);
        if (index.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> orderedArticleIds = index.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();

        List<Article> articles = articleRepository.findAllById(orderedArticleIds);
        Map<Long, Article> byId = articles.stream().collect(Collectors.toMap(Article::getId, a -> a, (a, b) -> a));

        ZoneId zone = ZoneId.of("Asia/Seoul");
        List<ChatHistoryListResDTO> out = new ArrayList<>();
        for (Long articleId : orderedArticleIds) {
            Article article = byId.get(articleId);
            if (article == null) {
                continue;
            }
            List<ChatMessagePair> history = loadAll(sid, articleId);
            if (history.isEmpty()) {
                continue;
            }
            ChatMessagePair last = history.get(history.size() - 1);
            Long epochMs = index.get(articleId);
            LocalDateTime lastChatAt = epochMs != null
                    ? LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
                    : LocalDateTime.now(zone);
            out.add(ChatHistoryListResDTO.fromAnonymous(
                    article, last.question(), last.answer(), lastChatAt));
        }
        return out;
    }

    public void appendTurn(String sessionId, Long articleId, String question, String answer, int maxTurns) {
        if (!isValidSessionId(sessionId) || articleId == null) {
            return;
        }
        String key = buildKey(sessionId, articleId);
        try {
            List<ChatMessagePair> list = new ArrayList<>(loadAll(sessionId, articleId));
            list.add(new ChatMessagePair(question, answer));
            if (maxTurns > 0 && list.size() > maxTurns) {
                list = new ArrayList<>(list.subList(list.size() - maxTurns, list.size()));
            }
            String json = objectMapper.writeValueAsString(list);
            redisUtil.setData(key, json, ttlSeconds);
            touchIndex(sessionId.trim(), articleId);
        } catch (Exception e) {
            log.error("[AnonymousChat] 저장 실패 sessionId={}, articleId={}", sessionId, articleId, e);
        }
    }

    private List<ChatMessagePair> loadAll(String sessionId, Long articleId) {
        if (!isValidSessionId(sessionId) || articleId == null) {
            return Collections.emptyList();
        }
        String key = buildKey(sessionId, articleId);
        try {
            String json = redisUtil.getData(key);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("[AnonymousChat] 조회 실패 sessionId={}, articleId={}: {}", sessionId, articleId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private static String buildKey(String sessionId, Long articleId) {
        return KEY_PREFIX + sessionId.trim() + ":" + articleId;
    }

    private static String buildIndexKey(String sessionId) {
        return INDEX_PREFIX + sessionId.trim();
    }

    private Map<Long, Long> loadIndexMap(String sessionId) {
        String key = buildIndexKey(sessionId);
        try {
            String json = redisUtil.getData(key);
            if (json == null || json.isBlank()) {
                return new HashMap<>();
            }
            Map<String, Long> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            Map<Long, Long> out = new HashMap<>();
            for (Map.Entry<String, Long> e : raw.entrySet()) {
                out.put(Long.parseLong(e.getKey()), e.getValue());
            }
            return out;
        } catch (Exception e) {
            log.warn("[AnonymousChat] 인덱스 파싱 실패 sessionId={}: {}", sessionId, e.getMessage());
            return new HashMap<>();
        }
    }

    private void saveIndexMap(String sessionId, Map<Long, Long> map) throws Exception {
        String key = buildIndexKey(sessionId);
        Map<String, Long> forJson = new LinkedHashMap<>();
        map.forEach((k, v) -> forJson.put(String.valueOf(k), v));
        String json = objectMapper.writeValueAsString(forJson);
        redisUtil.setData(key, json, ttlSeconds);
    }

    private void touchIndex(String sessionId, Long articleId) {
        try {
            Map<Long, Long> map = loadIndexMap(sessionId);
            map.put(articleId, System.currentTimeMillis());
            saveIndexMap(sessionId, map);
        } catch (Exception e) {
            log.warn("[AnonymousChat] 인덱스 갱신 실패 sessionId={}, articleId={}", sessionId, articleId, e);
        }
    }

    private void ensureIndexContains(String sessionId, Long articleId) {
        try {
            Map<Long, Long> map = loadIndexMap(sessionId);
            if (!map.containsKey(articleId)) {
                map.put(articleId, System.currentTimeMillis());
                saveIndexMap(sessionId, map);
            }
        } catch (Exception e) {
            log.warn("[AnonymousChat] 인덱스 보정 실패 sessionId={}, articleId={}", sessionId, articleId, e);
        }
    }
}
