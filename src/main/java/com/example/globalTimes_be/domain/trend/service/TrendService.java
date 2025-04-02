package com.example.globalTimes_be.domain.trend.service;

import com.example.globalTimes_be.domain.trend.dto.resonse.TrendDTO;
import com.example.globalTimes_be.domain.trend.exception.TrendErrorStatus;
import com.example.globalTimes_be.global.exception.BaseException;
import com.example.globalTimes_be.global.redis.RedisUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
@Service
public class TrendService {
    private final RedisUtil redisUtil;
    private static final long EXPIRATION_TIME = 86400 * 2 + 600; // 48시간 + 10분 (다음날 실검 없을 경우 대비)

    //검색어 저장
    public void saveTrendKeywords(String countryCode, List<TrendDTO> keywords) {
        //나라별 코드로 키 값 생성
        String key = "trend:" + countryCode;

        try {
            //json형식으로 직렬화
            String jsonData = TrendDTO.toJson(keywords);
            
            //redis에 저장
            redisUtil.setData(key, jsonData, EXPIRATION_TIME);
        } catch (JsonProcessingException e) {
            throw new BaseException(TrendErrorStatus._FAIL_SERIALIZATION.getResponse());
        }
    }

    //검색어 삭제
    public void deleteTrendKeywords(String countryCode) {
        //나라별 코드로 키 값 생성
        String key = "trend:" + countryCode;

        redisUtil.deleteData(key);
    }

    //검색어 조회
    public List<TrendDTO> getTrendingKeywords(String countryCode) {
        //나라 코드 리스트 생성
        List<String> countries = new ArrayList<>
                (Arrays.asList("KR", "AU", "AT", "BR", "CA", "CO", "DK", "EG", "FR", "DE", "GR", "HK", "IN",
                        "ID", "IT", "JP", "MY", "MX", "NL", "RU", "SG", "TW", "TR", "GB", "US", "ES"));

        //저장된 국가코드가 아닐 경우 에러처리
        if (!countries.contains(countryCode)) {
            throw new BaseException(TrendErrorStatus._INVALID_COUNTRY_CODE.getResponse());
        }

        //키 생성
        String key = "trend:" + countryCode;

        //redis에서 키로 값을 검색
        if (redisUtil.existData(key)) {
            try {
                //redis에서 값 추출
                String jsonData = redisUtil.getData(key);
                return TrendDTO.fromJson(jsonData);
            } catch (JsonProcessingException e) {
                throw new BaseException(TrendErrorStatus._FAIL_DESERIALIZATION.getResponse());
            }
        }
        return List.of();
    }
}
