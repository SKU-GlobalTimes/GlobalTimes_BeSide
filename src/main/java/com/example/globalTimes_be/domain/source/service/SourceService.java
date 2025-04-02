package com.example.globalTimes_be.domain.source.service;

import com.example.globalTimes_be.domain.source.entity.Source;
import com.example.globalTimes_be.domain.source.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SourceService {

    private final SourceRepository sourceRepository;

    // 중복 체크 후 Source 조회 또는 생성
    @Transactional
    public Source getOrCreateSource(String sourceName, String sourceId) {
        if (sourceName == null || sourceName.isEmpty()) {
            return null;
        }

        // 먼저 조회하여 중복 방지
        Optional<Source> existingSource = sourceRepository.findBySourceName(sourceName);
        if (existingSource.isPresent()) {
            return existingSource.get();
        }

        // 중복 예외 발생 시 다시 조회하여 해결
        try {
            Source newSource = Source.createSource(sourceName, sourceId);
            return sourceRepository.save(newSource);
        } catch (DataIntegrityViolationException e) {
            // 💡 중복 예외 발생 시 다시 조회하여 해결
            return sourceRepository.findBySourceName(sourceName)
                    .orElseThrow(() -> new RuntimeException("Source 저장 중 중복 발생, 하지만 조회 실패"));
        }
    }

    // 기존 Source 들을 미리 로드해서 캐싱
    @Transactional
    public Map<String, Source> preloadSources(List<String> sourceNames) {
        List<Source> existingSources = sourceRepository.findBySourceNameIn(sourceNames);

        // 기존 Source 를 Map 으로 변환
        Map<String, Source> sourceCache = existingSources.stream()
                .collect(Collectors.toMap(Source::getSourceName, source -> source));

        // 없는 Source 들은 새로 생성 후 저장
        for (String sourceName : sourceNames) {
            sourceCache.computeIfAbsent(sourceName, name -> getOrCreateSource(name, null));
        }

        return sourceCache;
    }
}
