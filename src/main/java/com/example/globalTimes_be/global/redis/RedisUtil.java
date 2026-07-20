package com.example.globalTimes_be.global.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisZSetCommands.ZAddArgs;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

    public List<String> getListData(String key) {
        List<String> values = template.opsForList().range(key, 0, -1);
        return values != null ? values : Collections.emptyList();
    }

    public void appendListData(String key, String value, long maxSize, long duration) {
        ListOperations<String, String> operations = template.opsForList();
        operations.rightPush(key, value);
        if (maxSize > 0) {
            operations.trim(key, -maxSize, -1);
        }
        template.expire(key, Duration.ofSeconds(duration));
    }

    public List<ScoredValue> getReverseSortedSetData(String key) {
        Set<ZSetOperations.TypedTuple<String>> values = template.opsForZSet()
                .reverseRangeWithScores(key, 0, -1);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream()
                .filter(value -> value.getValue() != null && value.getScore() != null)
                .map(value -> new ScoredValue(value.getValue(), value.getScore()))
                .toList();
    }

    public void addSortedSetDataIfGreater(String key, String value, double score, long duration) {
        byte[] rawKey = Objects.requireNonNull(template.getStringSerializer().serialize(key));
        byte[] rawValue = Objects.requireNonNull(template.getStringSerializer().serialize(value));
        template.execute((RedisCallback<Boolean>) connection ->
                connection.zSetCommands().zAdd(rawKey, score, rawValue, ZAddArgs.empty().gt()));
        template.expire(key, Duration.ofSeconds(duration));
    }

    public void addSortedSetDataIfAbsent(String key, String value, double score, long duration) {
        Boolean added = template.opsForZSet().addIfAbsent(key, value, score);
        if (Boolean.TRUE.equals(added)) {
            template.expire(key, Duration.ofSeconds(duration));
        }
    }

    //데이터 삭제
    public void deleteData(String key) {
        template.delete(key);
    }

    public record ScoredValue(String value, double score) {
    }
}
