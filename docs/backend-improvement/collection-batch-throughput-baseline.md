# RSS/News API 수집 배치 처리량 및 지연 전파 기준선

## 1. 목적

RSS/News API 수집은 단일 인스턴스에서 source를 순차 처리하고, 응답 단위 URL 일괄 중복 조회와 `saveAll`을 사용한다.
기존 #196, #212, #223은 중복 방지, 부분 실패 격리, 수집 통계를 검증했지만 배치 규모 증가에 따른 실제 MySQL 처리량과 순차 source 지연 전파는 측정하지 않았다.

이 문서는 통제된 fixture와 mock upstream으로 현재 구조의 처리 여유를 확인하고, 단순 DB 개선이나 별도 worker·durable message queue가 필요한지 판단하는 기준선을 남긴다.

## 2. 측정 경계

- Windows 로컬 노트북, 메모리 8GB
- Java 17, Docker Desktop `25.0.2`, MySQL `8.0` Testcontainers
- `news-fetch.enabled=false` 유지
- 실제 News API, RSS, 번역, Gemini 호출 0회
- 100·500·1,000건 News API 규모 측정은 DTO fixture 생성을 먼저 완료하고, timer 시작 후 `processArticlesWithStats()`와 MySQL 저장만 측정
- RSS 규모 측정은 XML fixture 생성과 Jsoup parsing을 먼저 완료해 `Elements`를 만들고, timer 시작 후 `processItemsWithStats()`와 MySQL 저장만 측정
- fixture 생성, News DTO 생성, RSS XML 생성·Jsoup parsing 시간은 규모별 elapsed와 rows/s에서 제외
- source 지연 전파 측정만 timer 안에서 local random-port HTTP 요청, JSON 역직렬화, `fetchTopHeadlines()` 처리와 JPA/MySQL 저장까지 전체 News API 경로를 실행
- 각 규모는 warm-up 후 3회 실행하고 elapsed 중앙값을 사용
- Hibernate SQL 콘솔 출력은 측정 테스트에서만 끄고 statistics statement 수는 유지
- 시간은 로컬 환경 영향을 받으므로 테스트 assertion으로 사용하지 않고 저장·중복·실패 격리만 회귀 조건으로 고정

운영 DB, schema/index, API, scheduler 설정, retry/circuit breaker, Kafka, 별도 queue, 분산 락은 변경하지 않았다.

## 3. Fixture와 실행 흐름

### 3.1 데이터 생성

`CollectionBatchPerformanceIntegrationTest`의 fixture 메서드가 입력을 만든다.

- `validNewsArticles(count, prefix)`: 서로 다른 URL과 10개 source를 순환하는 정상 News API 기사
- `rssItems(count, prefix)`: 서로 다른 URL의 RSS `<item>` XML을 생성하고 Jsoup parsing을 마친 `Elements` 반환
- `mixedNewsArticles()`: 정상 신규 700건, DB 기존 100건, 응답 내부 중복 100건, invalid 100건
- `handleMockUpstream(...)`: 정상, 300ms 지연, HTTP 500 source 응답

### 3.2 서비스 호출

- `measureNewsBatch(...)`는 미리 생성된 `List<NewsApiArticleDto>`를 받은 뒤 statistics와 timer를 초기화하고 `NewsApiService.processArticlesWithStats()`를 호출한다.
- `measureRssBatch(...)`는 XML 생성·Jsoup parsing이 끝난 `Elements`를 받은 뒤 statistics와 timer를 초기화하고 `RssNewsService.processItemsWithStats()`를 호출한다.
- `measureMockUpstreamScenario(...)`는 `fetchTopHeadlines()` 호출 직전에 timer를 시작하므로 실제 local HTTP 요청, JSON 역직렬화, service 처리와 JPA/MySQL 저장을 모두 포함한다.
- 첫 실행 후 같은 fixture를 다시 전달해 기존 URL 일괄 조회와 저장 0건을 확인한다.
- 모든 저장은 임시 MySQL 8 Testcontainers에서 실행되고 테스트 종료 후 폐기된다.

## 4. 배치 규모별 결과

### 4.1 News API fixture

| 응답 건수 | 신규 저장 | 신규 SQL statement | 신규 elapsed 중앙값 | 신규 처리량 | 재실행 저장 | 재실행 중복 | 재실행 SQL | 재실행 elapsed |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 | 100 | 122 | 605.06ms | 165.27 rows/s | 0 | 100 | 1 | 22.05ms |
| 500 | 500 | 522 | 1,942.33ms | 257.42 rows/s | 0 | 500 | 1 | 29.63ms |
| 1,000 | 1,000 | 1,022 | 4,244.38ms | 235.61 rows/s | 0 | 1,000 | 1 | 53.83ms |

10개 source를 처음 생성하는 조회·INSERT와 기사별 INSERT 때문에 신규 statement는 기사 수에 비례했다.
재실행은 URL 목록을 한 번에 조회한 뒤 저장하지 않아 규모별 SQL statement가 1개로 유지됐다.

### 4.2 RSS fixture

| 응답 건수 | 신규 저장 | 신규 SQL statement | 신규 elapsed 중앙값 | 신규 처리량 | 재실행 저장 | 재실행 중복 | 재실행 SQL | 재실행 elapsed |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 | 100 | 103 | 523.04ms | 191.19 rows/s | 0 | 100 | 2 | 22.95ms |
| 500 | 500 | 503 | 1,929.99ms | 259.07 rows/s | 0 | 500 | 2 | 25.22ms |
| 1,000 | 1,000 | 1,003 | 2,860.14ms | 349.63 rows/s | 0 | 1,000 | 2 | 56.15ms |

RSS 신규 statement는 기사별 INSERT와 source 조회·생성, 기존 URL 조회로 구성됐다.
처리량이 단조 증가하지 않는 것은 짧은 로컬 실행의 JIT, Docker, 파일·콘솔 및 host 부하 영향이 섞이기 때문이며 운영 capacity로 일반화하지 않는다.

### 4.3 혼합 News API fixture

| received | invalid | duplicate | saved | SQL statement | elapsed | 처리량 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1,000 | 100 | 200 | 700 | 702 | 3,973.96ms | 251.64 rows/s |

기존 100건과 신규 700건을 합쳐 DB에는 800건이 남았고 URL 중복 group은 0건이었다.

## 5. 순차 source 지연 전파

두 시나리오는 source 4개, HTTP 500 source 1개, 정상 저장 75건으로 조건을 맞췄다.
차이는 두 번째 source가 즉시 응답하는지 300ms 후 응답하는지뿐이다.

| 시나리오 | 요청 | 저장 | 실패 source | elapsed 중앙값 |
| --- | ---: | ---: | ---: | ---: |
| all-fast control | 4 | 75 | 1 | 506.69ms |
| 300ms delayed | 4 | 75 | 1 | 790.89ms |
| 차이 | - | - | - | 284.20ms |

300ms source 지연이 전체 순차 실행에 약 284ms 추가됐다.
중간 HTTP 500은 로그로 격리됐고 이후 source 요청과 정상 기사 저장은 계속됐다.

## 6. 판단

### 현재 구조

- 신규 1,000건은 로컬 MySQL에서 약 2.86~4.24초에 저장됐다.
- News API 4시간, RSS 6시간 주기와 비교하면 통제된 1,000건 배치가 다음 실행과 겹칠 근거는 없다.
- 동일 배치 재실행은 1,000건에서도 약 54~56ms, SQL 1~2개, 추가 저장 0건이었다.
- source 지연은 순차 실행 전체 시간에 그대로 더해지지만 source 1건의 5xx가 이후 수집을 막지는 않았다.

따라서 현재 데이터 규모와 단일 인스턴스에서는 Kafka나 durable message queue를 도입할 근거가 부족하다.
Issue #233으로 Phase 1의 수집 안정성·처리량 기준선은 마무리한다. 프로젝트 전체 Phase 1은 아래 잔여 안정성·문서 이슈를 완료한 뒤 종료하고, 신규 기술 도입은 Phase 2의 별도 문제와 지표를 기준으로 판단한다.

### 후속 검토 조건

- 수집 한 번이 다음 4/6시간 scheduler 실행과 겹치거나 지속적인 backlog가 발생한다.
- 작업 유실 방지, 재처리·replay, source별 독립 retry가 필요하다.
- 수집·번역·임베딩을 독립 consumer로 분리하고 각 단계를 별도 scale-out해야 한다.
- 수만 건 배치에서 기사별 INSERT가 확인 가능한 DB 병목이 된다.

마지막 조건만 발생한다면 Kafka보다 JDBC/JPA insert batching 또는 저장 단위 조정이 먼저다.
현재 수치만으로 구현을 변경하면 복잡도 대비 효과가 작으므로 이번 범위에서는 측정과 보류 판단으로 끝낸다.

## 7. Phase 1 종료 로드맵

1. Trend Gemini의 손상된 system prompt를 복구하고 configurable timeout과 upstream·timeout·내부 오류 경계를 mock 테스트로 고정한다.
2. Trend Redis 갱신의 선행 DELETE를 제거해 새 값 생성·저장 실패 시 기존 값을 보존한다.
3. 루트 README의 오래된 CI/CD 설명을 현재 상태로 고치고, #223 이후 결과를 정량 인덱스에 연결한 Phase 1 구조·근거 맵을 완성한다.

`NewsApiService`의 singleton 실행 카운터는 현재 기본 단일 scheduler에서 동시 실행 근거가 없으므로 Phase 1 필수 작업으로 확장하지 않는다. scheduler 병렬화나 수동 동시 실행 요구가 생길 때 실행별 지역 상태 전환을 검토한다.

## 8. 재현

```powershell
.\gradlew.bat test --tests "com.example.globalTimes_be.externalApi.service.CollectionBatchPerformanceIntegrationTest" --rerun-tasks
```

로그의 `COLLECTION_BATCH_MEDIAN`, `COLLECTION_BATCH`, `COLLECTION_UPSTREAM_COMPARISON` 행에서 측정값을 확인한다.
