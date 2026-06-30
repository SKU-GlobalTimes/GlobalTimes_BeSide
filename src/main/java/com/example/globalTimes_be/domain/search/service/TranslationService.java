package com.example.globalTimes_be.domain.search.service;

import com.example.globalTimes_be.global.redis.RedisUtil;
import com.example.globalTimes_be.global.translate.TranslateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class TranslationService {
    private final TranslateUtil translateUtil;
    private final RedisUtil redisUtil;

    //redis에서 변역결과를 조회하고 없으면 구글 번역
    public String translateToEnglish(String text){
        long startedAt = System.nanoTime();

        // Redis에서 text 조회
        String cachedTranslation = redisUtil.getData("translation:" + text);

        // Redis에 저장된 번역본이 있다면 바로 반환
        if (cachedTranslation != null){
            log.info("[Translation] cacheHit=true elapsedMs={} textLength={}",
                    elapsedMs(startedAt), text.length());
            return cachedTranslation;
        }

        // 구글번역 (API 키 미설정 또는 호출 실패 시 원문 그대로 사용)
        try {
            long externalCallStartedAt = System.nanoTime();
            String translatedText = translateUtil.translateToEnglish(text);
            long externalCallMs = elapsedMs(externalCallStartedAt);
            redisUtil.setData("translation:" + text, translatedText, 86400);
            log.info("[Translation] cacheHit=false externalCall=true externalCallMs={} elapsedMs={} textLength={} translatedLength={}",
                    externalCallMs, elapsedMs(startedAt), text.length(), translatedText.length());
            return translatedText;
        } catch (Exception e) {
            log.warn("[Translation] cacheHit=false externalCall=true fallback=original elapsedMs={} textLength={} reason={}",
                    elapsedMs(startedAt), text.length(), e.getMessage());
            return text;
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

}
