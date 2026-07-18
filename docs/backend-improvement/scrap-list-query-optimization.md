# 스크랩 목록 일괄 조회 최적화

- Issue: [#216](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/216)
- PR: [#217](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/217)
- 상태: `Done`

## 문제

스크랩 조회에는 서로 다른 두 경로가 있다.

- 로그인 목록은 모든 `Scrap` entity를 조회한 뒤 DTO 변환 중 `Article`과 `Source` LAZY 연관관계에 접근한다.
- 비로그인 localStorage 호환 목록은 요청받은 article ID마다 `findById()`를 실행하고 `Source`에 LAZY 접근한다.

따라서 서로 다른 기사와 Source가 `N`개일 때 로그인 목록은 `1 + 2N`, 비로그인 목록은 `2N`개의 SQL을 실행하는 구조였다.

## 검증 조건

- MySQL 8 Testcontainers
- 사용자 1명
- 서로 다른 Source 100개와 Article 100개
- 사용자의 Scrap 100개
- Hibernate Statistics로 SQL statements와 entity loads 측정
- 로그인 DTO 7개 필드 전체와 스크랩 최신순 비교
- 비로그인 DTO 6개 필드 전체, 요청 ID 순서, 중복 ID, 존재하지 않는 ID 제외 비교
- nullable `Article.source`가 목록에서 누락되지 않는지 확인

이 fixture는 SQL 증가 구조를 통제해 재현하기 위한 합성 데이터다. 측정 시점 로컬 DB의 실제 scrap은 1건이므로 운영 데이터 규모나 운영 latency를 나타내지 않는다.

## 개선

로그인 목록은 `Scrap`, `Article`, `Source`에서 응답에 필요한 컬럼만 interface projection으로 조회한다. `created_at DESC, scrap_id DESC`로 최신순과 timestamp 동률 순서를 결정적으로 유지한다.

비로그인 목록은 중복을 제거한 article ID 목록을 projection query 한 번으로 조회한다. 조회 결과를 ID map으로 만든 뒤 원래 요청 ID를 다시 순회해 요청 순서와 중복을 복원하고 존재하지 않는 ID는 기존처럼 제외한다. 빈 ID 목록은 DB를 호출하지 않는다.

`source_id`는 nullable이므로 두 query 모두 `LEFT JOIN`을 사용해 Source가 없는 Article 자체가 목록에서 사라지지 않게 했다.

## 결과

| 경로 | 지표 | 기존 | 개선 | 변화 |
| --- | --- | ---: | ---: | ---: |
| 로그인 | SQL statements | 201 | 1 | 99.5% 감소 |
| 로그인 | entity loads | 300 | 0 | projection 전환 |
| 로그인 | service elapsed | 945~1,185ms | 36~87ms | 실행별 92.7~96.2% 감소 |
| 비로그인 | SQL statements | 200 | 1 | 99.5% 감소 |
| 비로그인 | entity loads | 200 | 0 | projection 전환 |
| 비로그인 | service elapsed | 1,130~1,284ms | 40~90ms | 실행별 93.0~96.5% 감소 |

시간은 8GB 로컬 노트북에서 최종 `LEFT JOIN` 구현을 집중·전체 회귀로 실행한 두 관측값이다. JVM warm-up과 DB cache의 영향을 크게 받으므로 구조적으로 반복되는 SQL·entity load 감소를 핵심 결과로 본다.

`EXPLAIN ANALYZE`에서 로그인 query는 100행 정렬과 Article·Source join을 약 `1.50~1.54ms`, 비로그인 query는 100개 ID의 Article·Source left join을 약 `0.33~0.37ms`에 완료했다.

## 적용 범위

- API endpoint와 응답 DTO 필드는 변경하지 않는다.
- 로그인 목록의 스크랩 최신순과 비로그인 목록의 요청순·중복·누락 처리를 유지한다.
- 실제 스크랩 1건에서 즉시 필요한 운영 최적화로 과장하지 않는다.
- pagination, Redis, 신규 인덱스/Flyway, scrap toggle 동시성은 이번 범위에서 제외한다.
- 요청 ID나 사용자 스크랩 수가 API payload·메모리 문제를 만들 정도로 증가할 때 pagination 또는 요청 개수 제한을 별도로 검토한다.
