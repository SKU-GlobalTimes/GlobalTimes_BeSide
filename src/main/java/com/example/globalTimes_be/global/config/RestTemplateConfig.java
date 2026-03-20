package com.example.globalTimes_be.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);  // 외부 서버 연결 최대 3초 대기
        factory.setReadTimeout(5_000);     // 응답 수신 최대 5초 대기
        return new RestTemplate(factory);
    }
}
