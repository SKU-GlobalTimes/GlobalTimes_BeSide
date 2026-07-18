# 채팅 목록 latest-per-article 조회 최적화

- Issue: [#214](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/214)
- PR: [#215](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/215)
- 상태: `Done`

## 문제

채팅 팝업 목록은 사용자별로 기사마다 마지막 대화 한 건만 반환한다. 기존 구현은 사용자의 모든 `ChatHistory`를 최신순으로 조회한 뒤 Java의 `LinkedHashMap`으로 기사별 첫 행을 선택했다.

`ChatHistory.article`은 `LAZY @ManyToOne`이므로 DTO가 기사 제목과 썸네일을 읽을 때 기사별 추가 조회도 발생했다. 사용자 한 명에게 채팅 이력 `N`건과 서로 다른 기사 `A`개가 있으면 목록 100개를 만들기 위해 채팅 `N`건을 entity로 적재하고 SQL `1 + A`개를 실행하는 구조였다.

## 검증 조건

- MySQL 8 Testcontainers
- 대상 사용자 1명
- 기사 100개
- 기사별 채팅 50개, 총 5,000개
- 다른 사용자의 더 최신 채팅 1개를 추가해 사용자 격리 검증
- 같은 `created_at`을 가진 마지막 두 채팅을 추가해 `chat_id DESC` 동률 처리 검증
- 응답 개수, 순서, 마지막 질문, 100자 답변 미리보기 규칙 유지

이 fixture는 쿼리 증가 양상을 재현하기 위한 합성 데이터다. 측정 시점 로컬 DB의 실제 `chat_history`는 4건이므로 아래 시간은 운영 성능이나 현재 사용자 규모를 나타내지 않는다.

## 후보 비교

처음 검토한 JPQL correlated `NOT EXISTS` 후보는 SQL 수를 1개로 줄였지만, `EXPLAIN ANALYZE`에서 외부 5,000행마다 후속 인덱스 조회를 반복하는 antijoin이 약 448ms 걸렸다.

최종 후보는 MySQL 8의 `ROW_NUMBER()`를 사용한다.

1. 대상 사용자의 채팅을 기사별로 나눈다.
2. `created_at DESC, chat_id DESC` 순으로 순번을 부여한다.
3. 순번 1인 행만 기사와 조인한다.
4. interface projection으로 응답에 필요한 컬럼만 읽는다.

## 결과

| 지표 | 기존 전체 이력 + Java 그룹화 | `ROW_NUMBER()` projection | 변화 |
| --- | ---: | ---: | ---: |
| 조회 대상 채팅 | 5,000 | 5,000 | 동일 fixture |
| 반환 목록 | 100 | 100 | 응답 의미 유지 |
| SQL statements | 101 | 1 | 99.0% 감소 |
| Hibernate entity loads | 5,100 | 0 | projection 전환 |
| service elapsed | 741~1,786ms | 126~235ms | 실행별 83.0~86.8% 감소, 약 5.9~7.6배 |

시간은 8GB 로컬 노트북의 동일 Testcontainers 테스트를 집중·전체 회귀로 두 번 실행해 관측한 범위다. 환경과 JVM warm-up에 따라 변할 수 있으므로 구조적으로 재현 가능한 SQL 수와 entity load 감소를 핵심 결과로 본다.

최종 `EXPLAIN ANALYZE`에서는 `chat_history` 5,001행을 한 번 scan하고 대상 5,000행에 window 함수를 적용해 100행을 반환했다. 실행 계획의 최상위 sort 완료 시점은 약 17.6ms였으며 correlated antijoin의 반복 lookup은 사라졌다.

## 적용 범위

- 채팅 목록 API의 응답 필드와 최신순 정렬을 유지한다.
- 동일 시각에는 더 큰 `chat_id`를 최신 대화로 선택해 결과를 결정적으로 만든다.
- 상세 페이지의 기사별 전체 대화 조회는 변경하지 않는다.
- 현재 실제 데이터 규모에서 근거가 부족한 신규 인덱스, Flyway migration, pagination, Redis cache는 추가하지 않는다.
- 데이터가 증가해 실행 계획이나 목록 응답 크기가 다시 문제가 될 때 pagination과 복합 인덱스를 별도 측정한다.
