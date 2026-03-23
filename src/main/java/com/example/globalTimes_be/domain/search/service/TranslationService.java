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
        // Redis에서 text 조회
        String cachedTranslation = redisUtil.getData("translation:" + text);

        // Redis에 저장된 번역본이 있다면 바로 반환
        if (cachedTranslation != null){
            log.info("Redis에 저장되어있는 번역 전: {}, 번역 후: {}", text, cachedTranslation);
            return cachedTranslation;
        }

        // 구글번역 (API 키 미설정 또는 호출 실패 시 원문 그대로 사용)
        try {
            String translatedText = translateUtil.translateToEnglish(text);
            log.info("Redis에 저장될 번역 전: {}, 번역 후: {}", text, translatedText);
            redisUtil.setData("translation:" + text, translatedText, 86400);
            return translatedText;
        } catch (Exception e) {
            log.warn("[번역 실패] 원문으로 검색 진행: {} / 원인: {}", text, e.getMessage());
            return text;
        }
    }

}
