package com.example.globalTimes_be.domain.news.repository;

import com.example.globalTimes_be.domain.news.entity.NewsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsRepository extends JpaRepository<NewsEntity, Long> {
}
