package com.example.globalTimes_be.domain.trend.service;

import com.example.globalTimes_be.domain.trend.dto.resonse.TrendDTO;
import com.example.globalTimes_be.global.exception.BaseException;
import com.example.globalTimes_be.global.redis.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrendServiceTest {

    private static final String KEY = "trend:KR";
    private static final long EXPIRATION_TIME = 86400 * 2 + 600;

    private RedisUtil redisUtil;
    private TrendService trendService;

    @BeforeEach
    void setUp() {
        redisUtil = mock(RedisUtil.class);
        trendService = new TrendService(redisUtil);
    }

    @Test
    void replaceTrendKeywords_replacesValueWithSingleSetAfterSerialization() {
        TrendDTO trend = trend("new keyword");
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);

        trendService.replaceTrendKeywords("KR", List.of(trend));

        verify(redisUtil).setData(
                org.mockito.ArgumentMatchers.eq(KEY),
                jsonCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(EXPIRATION_TIME)
        );
        verify(redisUtil, never()).deleteData(anyString());
        assertThat(jsonCaptor.getValue())
                .contains("\"keyword\":\"new keyword\"")
                .contains("\"title\":\"title\"");
    }

    @Test
    void replaceTrendKeywords_doesNotChangeRedisWhenSerializationFails() {
        TrendDTO invalidTrend = new TrendDTO() {
            @Override
            public String getKeyword() {
                throw new IllegalStateException("fixture serialization failure");
            }
        };

        assertThatThrownBy(() -> trendService.replaceTrendKeywords("KR", List.of(invalidTrend)))
                .isInstanceOfSatisfying(BaseException.class, ex ->
                        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));

        verify(redisUtil, never()).setData(anyString(), anyString(), anyLong());
        verify(redisUtil, never()).deleteData(anyString());
    }

    @Test
    void replaceTrendKeywords_preservesExistingValueWhenRedisWriteFails() throws Exception {
        String existingJson = TrendDTO.toJson(List.of(trend("existing keyword")));
        when(redisUtil.existData(KEY)).thenReturn(true);
        when(redisUtil.getData(KEY)).thenReturn(existingJson);
        doThrow(new IllegalStateException("redis write failure"))
                .when(redisUtil).setData(anyString(), anyString(), anyLong());

        assertThatThrownBy(() -> trendService.replaceTrendKeywords("KR", List.of(trend("new keyword"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis write failure");

        List<TrendDTO> remaining = trendService.getTrendingKeywords("KR");

        assertThat(remaining).singleElement()
                .extracting(TrendDTO::getKeyword)
                .isEqualTo("existing keyword");
        verify(redisUtil, never()).deleteData(anyString());
    }

    private TrendDTO trend(String keyword) {
        return TrendDTO.builder()
                .keyword(keyword)
                .title("title")
                .sourceName("source")
                .url("https://example.com/" + keyword.replace(" ", "-"))
                .urlToImage("https://example.com/image.jpg")
                .build();
    }
}
