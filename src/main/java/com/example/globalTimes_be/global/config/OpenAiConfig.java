package com.example.globalTimes_be.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OpenAiConfig {

    // GPT 비활성 상태 - OPENAI_API_KEY 미설정 시에도 기동 가능하도록 기본값 처리
    // 복구 시: ${spring.openai.api-key} 로 변경하고 .env에 OPENAI_API_KEY 추가
    @Value("${spring.openai.api-key:disabled}")
    private String apiKey;

    @Bean
    public WebClient openAiWebClient(){
        return WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
