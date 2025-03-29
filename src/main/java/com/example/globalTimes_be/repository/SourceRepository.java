package com.example.globalTimes_be.repository;

import com.example.globalTimes_be.domain.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SourceRepository extends JpaRepository<Source, Long> {
    Optional<Source> findBySourceName(String sourceName);
}
