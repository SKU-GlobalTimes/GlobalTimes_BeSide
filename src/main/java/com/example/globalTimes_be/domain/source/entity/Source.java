package com.example.globalTimes_be.domain.source.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "source_id")
    private Long id;

    @Column(name = "source_name", unique = true, nullable = false)
    private String sourceName; // 중복 불가능 

    @Column(name = "source_api_id", nullable = true)
    private String sourceApiId; // 가끔 null 값이 온다.

    // 정적 팩토리 메소드
    public static Source createSource(String sourceName, String sourceId) {

        if (sourceName == null || sourceName.isEmpty()) {
            return null; // ex 터트리기 대신 null 반환
        }

        Source source = new Source();
        source.sourceName = sourceName;
        source.sourceApiId = sourceId;
        return source;
    }
}
