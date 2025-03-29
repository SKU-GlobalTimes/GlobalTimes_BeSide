package com.example.globalTimes_be.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
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

    @NotNull
    @Column(name = "source_name", unique = true)
    private String sourceName; // 중복 불가능 

    @Column(name = "source_api_id", nullable = true)
    private String sourceApiId; // 가끔 null 값이 온다.


    // 정적 팩토리 메소드
    public static Source createSource(String sourceName, String sourceId) {

        /*
        if (sourceName == null) {
            throw new IllegalArgumentException
        }
        언론사 id 말구 name 이 null 일 수가 있나 ? 만약에 보면 ex 터트리도록 하구
        ( 응 null 오더라 )
        */

        Source source = new Source();
        source.sourceName = sourceName;
        source.sourceApiId= sourceId;
        return source;
    }

}
