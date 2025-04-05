package com.example.globalTimes_be.domain.source.repository;

import com.example.globalTimes_be.domain.source.entity.Source;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SourceRepository extends JpaRepository<Source, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE) // 동시성 제어 추가
    Optional<Source> findBySourceName(String sourceName); // 단일 조회

    List<Source> findBySourceNameIn(List<String> sourceNames); // 여러 개 조회 ( 캐싱 용 )
}
