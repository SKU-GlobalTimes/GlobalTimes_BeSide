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

        // 구글번역
        String translatedText = translateUtil.translateToEnglish(text);

        log.info("Redis에 저장될 번역 전: {}, 번역 후: {}", text, translatedText);

        // 번역 결과 Redis에 캐싱 (24시간 유지)
        redisUtil.setData("translation:" + text, translatedText, 86400);

        return translatedText;
    }

}
