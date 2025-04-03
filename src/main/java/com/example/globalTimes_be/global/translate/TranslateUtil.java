package com.example.globalTimes_be.global.translate;

import com.example.globalTimes_be.global.exception.BaseException;
import com.example.globalTimes_be.global.translate.exception.TranslateErrorStatus;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TranslateUtil {
    private final Translate translate;

    // 생성자에서 클라이언트 초기화
    public TranslateUtil(@Value("${spring.google.api-key}") String apiKey){
        this.translate = TranslateOptions.newBuilder()
                .setApiKey(apiKey)
                .build()
                .getService();
    }

    //자동으로 언어 감지 후 영어로 번역
    public String translateToEnglish(String text){
        System.out.println("번역기에 들어온 텍스트: " + text);
        try {
            Translation translation = translate.translate(
                    text,
                    Translate.TranslateOption.targetLanguage("en"), // 영어로 번역
                    Translate.TranslateOption.model("base") // 기본 모델 사용
            );

            log.info("번역 전: {}, 번역 후: {}", text, translation.getTranslatedText());

            return translation.getTranslatedText();
        } catch (Exception e) {
            //번역 오류시 에러처리
            throw new BaseException(TranslateErrorStatus._TRANSLATE_ERROR.getResponse());
        }
    }
}
