package com.example.globalTimes_be.global.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;

@RequiredArgsConstructor
@Service
public class RedisUtil {
    private final StringRedisTemplate template;

    //데이터 가져오기
    public String getData(String key){
        ValueOperations<String, String> valueOperations = template.opsForValue();
        return valueOperations.get(key);
    }

    //데이터 존재 여부
    public boolean existData(String key){
        return Boolean.TRUE.equals(template.hasKey(key));
    }

    //데이터 생성
    public void setData(String key, String value) {
        ValueOperations<String, String> valueOperations = template.opsForValue();
        valueOperations.set(key, value);
    }

    //데이터 생성 및 파기시간
    public void setData(String key, String value, long duration){
        ValueOperations<String, String> valueOperations = template.opsForValue();
        Duration expireDuration = Duration.ofSeconds(duration);
        valueOperations.set(key, value, expireDuration);
    }

    //데이터 삭제
    public void deleteData(String key) {
        template.delete(key);
    }
}