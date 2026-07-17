# Article URL Uniqueness

## 문제

#196은 News API/RSS 응답 내부의 동일 URL을 `seenUrls`로 제거하고 DB 기존 URL을 일괄 조회해 순차 재실행 중복을 막았다.
그 이전 데이터에는 동일 응답에서 함께 저장된 것으로 보이는 URL 중복이 남아 있었다.

- 전체 기사: 9,853건
- 고유 URL: 9,814개
- 중복 URL 그룹: 38개
- 초과 중복 행: 39건
- 삭제 후보의 scrap/chat 참조: 0건
- 삭제 후보의 summary/crawled content/non-zero view count: 0건

URL과 source 분포에는 연합뉴스, BBC, SCMP, NHK, TechCrunch 등 실제 언론사 도메인이 사용됐고 test/example URL은 0건이었다. 이번 작업에서 `news-fetch.enabled`를 켜거나 외부 API를 다시 호출하지 않았다.

## V3 정책

`V3__enforce_article_url_uniqueness.sql`은 `url_hash`별 가장 큰 `article_id`를 최신 저장 행으로 보고 보존한다. MySQL 기본 collation의 대소문자/악센트 비구분 비교로 서로 다른 URL을 삭제하지 않도록 원문 URL의 SHA-256 hash를 동일성 기준으로 사용한다.

중복 데이터를 변경하기 전에 기존 `url_hash` generated column과 canonical index 구조를 먼저 검증한다. generated expression은 공백과 backtick을 정규화한 뒤 `UNHEX(SHA2(url, 256))`과 정확히 비교하므로, 잘못된 기존 구조에서는 행을 삭제하지 않고 migration이 실패한다.

삭제 후보에 아래 데이터가 하나라도 있으면 자동 삭제하지 않고 migration을 실패시킨다.

- scrap 또는 chat_history 참조
- summary 또는 crawled_content
- 0이 아닌 view_count

안전한 중복을 정리한 뒤 다음 generated column과 UNIQUE 인덱스를 추가한다.

```sql
url_hash BINARY(32)
    GENERATED ALWAYS AS (UNHEX(SHA2(url, 256))) STORED
```

```sql
UNIQUE INDEX uk_article_url_hash (url_hash)
```

현재 최대 URL 길이 406자를 기준으로 URL을 `VARCHAR`로 축소하거나 prefix UNIQUE를 사용하지 않았다. SHA-256 충돌 가능성은 이론적으로 0이 아니지만, 프로젝트 데이터 규모에서 긴 원문 URL을 보존하면서 고정 길이 인덱스를 사용하는 현실적인 DB 불변식으로 선택했다.

## Testcontainers 검증

- 신규 DB에서 Flyway V1→V2→V3 성공
- V2 상태의 안전 중복 2건을 V3가 최신 행 1건으로 정리
- 대소문자만 다른 URL은 서로 다른 hash와 행으로 유지
- summary 또는 scrap 참조가 있는 삭제 후보에서 V3 실패 및 원본·참조 데이터 유지
- 잘못된 generated column에서 중복 삭제 전 실패하고, 구조 수정·Flyway repair·재실행 성공
- V3 이후 동일 URL 동시 INSERT 2건 중 1건 성공, 1건 duplicate key 거부
- 기존 FK/FULLTEXT/transaction 회귀를 포함한 집중 테스트 10개 통과
- 전체 Gradle 테스트 53개 통과

## 로컬 DB 적용 결과

- Flyway version 3, type SQL, success 1
- 기사: 9,853 → 9,814건
- 고유 URL: 9,814 → 9,814개
- 초과 중복: 39 → 0건
- 생성된 url_hash: 9,814개, 고유 hash 9,814개
- scrap: 1 → 1건
- chat_history: 4 → 4건
- source: 673 → 673건
- migration procedure 잔여물: 0개

## 범위 경계

현재 단일 인스턴스의 수집 loop와 기본 scheduler는 순차 실행되고 #196 이후 응답 내부 중복도 제거된다. 따라서 V3는 현재 동시성 장애를 과장한 해결이 아니라 과거 데이터 정리와 DB 최종 불변식 보강이다.

실제 News API/RSS E2E, `news-fetch.enabled` 변경, Redis 분산 락, Kafka, 멀티 인스턴스 배포는 포함하지 않는다.
