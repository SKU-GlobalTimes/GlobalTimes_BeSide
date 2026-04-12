package com.example.globalTimes_be.domain.chat.service;

import com.example.globalTimes_be.domain.chat.dto.ChatMessagePair;
import com.example.globalTimes_be.global.redis.RedisUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnonymousChatSessionService {

    private static final String KEY_PREFIX = "chat:anon:";

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

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
     */
    public List<ChatMessagePair> getFullHistory(String sessionId, Long articleId) {
        return loadAll(sessionId, articleId);
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
}
