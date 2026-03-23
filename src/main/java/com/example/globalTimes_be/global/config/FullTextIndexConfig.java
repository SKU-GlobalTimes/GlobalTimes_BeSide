package com.example.globalTimes_be.global.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * FULLTEXT 인덱스는 JPA @Index로 생성 불가 → JdbcTemplate으로 직접 DDL 실행
 * IF NOT EXISTS 조건으로 중복 생성 방지
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FullTextIndexConfig {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void createFullTextIndex() {
        try {
            jdbcTemplate.execute(
                "CREATE FULLTEXT INDEX IF NOT EXISTS ft_article_title_description " +
                "ON article(title, description)"
            );
            log.info("[FULLTEXT 인덱스] ft_article_title_description 생성 완료 (또는 이미 존재)");
        } catch (Exception e) {
            log.warn("[FULLTEXT 인덱스] 생성 중 오류 발생 (무시): {}", e.getMessage());
        }
    }
}
