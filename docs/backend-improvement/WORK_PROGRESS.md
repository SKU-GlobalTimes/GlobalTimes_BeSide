# 백엔드 개선 작업 진척 노트

이 문서는 GlobalTimes 백엔드 개선 작업을 장기적으로 이어가기 위한 작업 진척 노트다.
Codex 대화 context가 사라지거나 새 세션에서 이어서 작업해야 할 때, 이 문서를 기준으로 현재까지의 의사결정, 완료 작업, 다음 작업을 복원한다.

## Current Active Work

- 없음

## Recently Completed

### #227 - Perspectives 실제 DB 정답 표본 Precision@5 기준선

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/227
- 작업 브랜치: `measure/#227-perspectives-quality-labeled-sample`
- 상태: `Done` ([PR #228](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/228))
- 기준선: 기존 실제 DB snapshot은 일부 결과를 good·partial·weak·no-match로 분류했지만 기대 관련 기사 ID, 고정 라벨 기준, Precision@5·Hit@5가 없어 실제 품질과 실패 원인을 수치로 반복 비교하기 어려웠다.
- 결과: 실제 DB 6건의 strict `Precision@5=0.267`, `Useful Precision@5=0.467`, `Hit@5=0.500`, 평균 반환 국가 수 `1.67`을 기록했다. 6건은 #146·#156·#160에서 이어받은 고정 진단 표본이며 전체 9,814건의 정확도로 해석하지 않는다.
- 원인 경계: 8148·8468은 candidate 0건이지만 coverage와 retrieval 원인을 확정하지 않으며, 후보가 없어 ranking은 평가하지 않았다.
- 번역 경로: 일일 general-model quota를 1,000자로 제한하고 비영어 표본 2건·총 52자만 호출했다. 프랑스어 표본은 710ms에 2개 국가·3개 same-event 기사를 반환했고 한국어 표본은 2,220ms에 candidate 0건이었다.
- 검증: read-only SQL 전체를 MySQL 8에서 실행했고 Backend CI가 통과했다. AI Reviewer는 Blocking 없음·MERGE_READY로 판정했다.
- 측정 문서: `docs/backend-improvement/perspectives-labeled-quality-baseline.md`
- 재현 SQL: `docs/backend-improvement/sql/perspectives-labeled-quality-sample.sql`

### #225 - Perspectives 다국어 번역·FULLTEXT 경로 원인 분리 회귀 검증

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/225
- 작업 브랜치: `test/#225-perspectives-multilingual-fulltext`
- 상태: `Done` ([PR #226](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/226))
- 기준선: 기존 `PerspectivesServiceTest`는 번역 실패 fallback을 mock repository로만 확인하며, 비영어 기준 기사에서 mock 번역·실제 MySQL FULLTEXT·원문 검색·결과 병합까지 이어지는 성공 경로는 자동 검증하지 않았다.
- 결과: 통제된 Testcontainers fixture에서 관련 기사 존재 여부와 mock 번역 결과만 바꿔 coverage 부재와 번역·matching 실패 조건을 분리하고, fallback·결과 병합·중복 제거·국가별 응답 회귀를 고정했다.
- overengineering 판단: 기존 MySQL Testcontainers·Flyway·Mockito를 재사용하고 운영 코드·schema·API·ranking·번역 저장은 변경하지 않았다. 실제 Google Translation API, News API/RSS, compose DB, Elasticsearch·Vector DB·Kafka도 사용하지 않았다.
- 검증: 5개 통제 시나리오에서 번역 성공 1건 반환, coverage 부재 0건, 오역 0건, 번역 실패 원문 fallback 1건, 번역·원문 중복 최종 1건을 확인했다. 전체 76개 테스트와 Backend CI가 통과했고 AI Reviewer는 Blocking 없음·MERGE_READY로 판정했다. 실제 유사도 정확성이나 외부 번역 품질 개선은 주장하지 않는다.
- 검증 문서: `docs/backend-improvement/perspectives-multilingual-fulltext-integration.md`

### #223 - RSS·News API source별 freshness·coverage 기준선 및 수집 통계 보강

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/223
- 작업 브랜치: `obs/#223-collection-freshness-coverage`
- 상태: `Done` ([PR #224](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/224))
- 기준선: 백엔드 poll 주기는 News API headline 4시간·Everything 1일·RSS 6시간으로 정해져 있지만 외부 source의 실제 갱신 시점과 신규 기사 수는 보장할 수 없다. 기존 로그는 응답·invalid·중복·저장 수만 제공해 Perspectives 결과 부족이 데이터 부재인지 매칭 실패인지 구분하기 어려웠다.
- 결과: News API·RSS batch 로그에 source·country·language·category, 응답·invalid·중복·저장 수, 최초·최신 발행 시각과 freshness를 추가하고 기존 DB 분포를 확인하는 read-only SQL을 남겼다.
- overengineering 판단: 기존 컬럼·로그·fixture를 활용하고 신규 schema·수집 이력 테이블·Prometheus/APM은 추가하지 않았다. 실제 News API/RSS·번역 API 호출, 영어 번역 저장, ranking·retry·Kafka 변경도 제외했다.
- 검증: fixture별 응답 4·invalid 1·중복 2·저장 1과 발행 시각 09:00~11:00 UTC를 확인했고 MySQL·Redis Testcontainers 포함 전체 71개 테스트와 Backend CI가 통과했다. 실행 중인 compose DB가 없어 실데이터 분포 수치는 주장하지 않았으며 AI Reviewer는 Blocking 없음·MERGE_READY로 판정했다.
- 작업 문서: `docs/backend-improvement/collection-freshness-coverage-baseline.md`
- 재현 SQL: `docs/backend-improvement/sql/collection-coverage-snapshot.sql`

### #221 - 익명 채팅 Redis 동시 요청 데이터 유실 방지 및 회귀 검증

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/221
- 작업 브랜치: `fix/#221-anonymous-chat-redis-concurrency`
- 상태: `Done` ([PR #222](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/222))
- 기준선: 익명 대화와 최근 기사 인덱스가 Redis String JSON의 read-modify-write로 저장되어 동일 세션의 동시 SSE 완료 시 마지막 SET이 앞선 갱신을 덮어쓸 수 있었다.
- 결과: Redis List·Sorted Set의 개별 추가 명령으로 전체 JSON 덮어쓰기를 제거하고, `ZADD GT`로 지연된 과거 최근 활동 score의 역전 갱신을 차단했다.
- overengineering 판단: 이미 사용하는 Redis 자료구조와 Spring Data Redis 기본 명령만 활용했다. Lua, MULTI/EXEC, 분산 락, Kafka, 로그인 MySQL 채팅 변경, 실제 Gemini 호출은 제외했다.
- 검증: 통제된 동시 요청 20건에서 기존 JSON은 1건 보존·19건 유실, List는 20건 보존·0건 유실을 3회 반복 확인했다. 서로 다른 기사 인덱스도 20/20건을 3회 보존했고 score 200 이후 도착한 score 100을 거부했다. Redis 집중 테스트 9개, 전체 67개 테스트, Backend CI가 통과했으며 AI Reviewer는 Blocking 없음·MERGE_READY로 판정했다.
- 측정 문서: `docs/backend-improvement/anonymous-chat-redis-concurrency.md`

### #218 - 백엔드 개선 정량 결과 인덱스 정리

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/218
- 작업 브랜치: `docs/#218-quantitative-improvement-index`
- 상태: `Done` ([PR #220](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/220))
- 기준선: k6·mock server·Testcontainers·EXPLAIN·Actuator 기반 개선 결과가 개별 PR과 측정 문서에 분산되어 문제·조치·결과의 연결을 다시 추적하기 어려웠다.
- 결과: 실제 개선, 성능 기준선, capacity 경계, 장애·정합성 검증을 구분하고 도메인별 문제·조치·정량 결과를 원본 PR과 상세 문서로 연결했다.
- overengineering 판단: 새 측정 시스템이나 기술을 추가하지 않고 기존 근거를 한 문서로 인덱싱했다. production code·test·k6 script·설정과 Kafka·Vector DB 도입은 변경하지 않았다.
- 검증: 원본 문서의 환경·RPS·p95·SQL·정합성 수치와 PR 링크를 교차 확인하고 로컬·mock·합성 fixture의 표현 한계를 명시했다. 상대 Markdown 링크 17개와 Backend CI가 통과했다.
- 결과 문서: `docs/backend-improvement/backend-improvement-quantitative-index.md`

### #216 - 스크랩 목록 N+1 및 ID별 반복 조회 제거

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/216
- 작업 브랜치: `perf/#216-scrap-list-query`
- 상태: `Done` ([PR #217](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/217))
- 기준선: 로그인 목록은 `Scrap → Article → Source` LAZY 접근으로 100건에서 SQL 201개·entity load 300개, 비로그인 호환 목록은 article ID별 `findById()` 반복으로 SQL 200개·entity load 200개가 MySQL fixture에서 발생했다.
- 목표: MySQL 8 Testcontainers의 동일 100건 fixture에서 projection 일괄 조회로 교체하면서 DTO 전체 필드와 최신순·요청순·중복·누락 처리를 유지한다.
- overengineering 판단: 실제 로컬 스크랩은 1건이라 현재 운영 병목으로 과장하지 않는다. 기존 JPA/MySQL query와 회귀 테스트만 사용했으며 pagination, Redis, 신규 인덱스/Flyway, toggle 동시성 변경은 제외했다.
- 검증: 서로 다른 Source·Article·Scrap 100건 fixture에서 로그인 SQL `201 → 1`, entity load `300 → 0`, 비로그인 SQL `200 → 1`, entity load `200 → 0`을 확인했다. DTO 전체 필드, 최신순, 요청순, 중복·누락 ID, nullable Source를 회귀 검증했고 전체 58개 테스트와 Backend CI가 통과했다.
- 측정 문서: `docs/backend-improvement/scrap-list-query-optimization.md`

### #214 - 채팅 목록 전체 이력 로딩 및 N+1 제거

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/214
- 작업 브랜치: `perf/#214-chat-latest-per-article`
- 상태: `Done` ([PR #215](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/215))
- 기준선: 사용자 채팅 5,000건·기사 100개 fixture에서 전체 `ChatHistory`를 적재하고 Java로 기사별 최신 행을 선택해 SQL 101개와 entity load 5,100개가 발생했다.
- 목표: MySQL 8 `ROW_NUMBER()`와 interface projection으로 사용자·기사별 최신 대화만 한 번에 조회하면서 목록 순서, 동률 처리, 답변 미리보기를 유지한다.
- overengineering 판단: 로컬 실제 채팅은 4건뿐이지만 전체 이력 로딩과 N+1은 데이터 증가에 선형으로 악화되는 명확한 구조 문제다. 기존 MySQL/JPA 안에서 쿼리와 회귀 테스트만 바꾸고 근거가 부족한 신규 인덱스·Flyway·pagination·Redis는 제외했다.
- 검증: 합성 fixture의 집중·전체 회귀 실행에서 SQL `101 → 1`, entity load `5,100 → 0`, service elapsed 실행별 `83.0~86.8%` 감소를 관측했다. 실행 계획은 대상 5,000행을 한 번 materialize해 100행을 반환했고 사용자 격리, `created_at` 동률 시 큰 `chat_id`, 100자 미리보기 규칙을 확인했다. 전체 55개 테스트와 Backend CI가 통과했다.
- 측정 문서: `docs/backend-improvement/chat-history-latest-query.md`

### #212 - News API 수집 부분 실패 격리 및 재실행 E2E 검증

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/212
- 작업 브랜치: `test/#212-news-api-failure-e2e`
- 상태: `Done` ([PR #213](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/213))
- 기준선: 기존 테스트는 News API DTO 처리와 mock repository까지만 검증해 HTTP 5xx·timeout이 실제 MySQL 저장과 후속 요청에 미치는 영향을 자동 확인하지 않았다.
- 목표: local mock upstream의 200·500·timeout 혼합 응답을 실제 RestTemplate과 MySQL Testcontainers로 통과시켜 부분 실패 격리와 재실행 중복 0건을 검증한다.
- overengineering 판단: 기존 Spring·Testcontainers와 JDK HTTP server만 사용하고 실제 API, RSS, retry/circuit breaker, 분산 락·Kafka는 제외한다.
- 검증: general/technology 정상 기사 2건은 저장되고 business 500과 science timeout 이후에도 수집이 계속됐다. 같은 4-category 시나리오를 두 번 실행한 뒤 기사 2건과 `url_hash` 중복 그룹 0건이 유지됐으며 전체 54개 테스트와 Backend CI가 통과했다.

### #210 - 기사 URL 중복 정리 및 DB 유일성 제약 보강

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/210
- 작업 브랜치: `data/#210-article-url-uniqueness`
- 상태: `Done` ([PR #211](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/211))
- 기준선: #196 이전 동일 응답 중복 방어가 없던 시기의 흔적으로 기사 9,853건 중 고유 URL은 9,814개이며 38개 URL 그룹에 초과 행 39건이 남아 있었다.
- 목표: 보존 데이터가 없는 과거 중복만 Flyway V3로 정리하고, `SHA2(url, 256)` generated column UNIQUE로 DB가 URL 유일성을 최종 보장한다.
- overengineering 판단: 실제 중복이 존재하고 Flyway 기반이 준비돼 DB 제약은 적절하다. 외부 수집 E2E, Redis 분산 락, Kafka, 멀티 인스턴스 구성은 현재 단일 순차 수집 범위에 과해 제외한다.
- 검증: Testcontainers에서 raw SHA-256 hash 기준 안전 중복 정리와 대소문자 URL 보존, summary/scrap 참조 중복 차단, 잘못된 generated column의 삭제 전 실패·repair·재실행, 동일 URL 동시 INSERT 1건 성공/1건 거부를 확인했다. 집중 테스트 10개와 전체 53개 테스트 및 Backend CI가 통과했다. 로컬 V3 적용 후 기사 9,814건·고유 URL 9,814개·중복 0건이며 scrap 1, chat 4, source 673은 유지됐다.

### #208 - Flyway 기반 스키마 기준선 및 Testcontainers 재현성 확보

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/208
- 작업 브랜치: `db/#208-flyway-baseline`
- 상태: `Done` ([PR #209](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/209))
- 기준선: 애플리케이션 시작 시 Hibernate DDL과 `FullTextIndexConfig`가 스키마를 암묵적으로 보완해, 빈 DB·개발 DB·CI DB가 같은 구조에서 시작하는지 버전으로 추적할 수 없다.
- 목표: 5개 도메인 테이블을 Flyway V1으로 관리하고, V2에서 legacy constraint/index 이름과 기사 FULLTEXT 인덱스를 canonical 구조로 수렴시킨다. Hibernate는 `ddl-auto=validate`로 migration 결과만 검증한다.
- overengineering 판단: 현재 스키마를 V1으로 고정하고 재현 테스트만 추가한다. URL UNIQUE, 중복 데이터 정리, 신규 테이블·컬럼, 운영 배포 자동화는 별도 근거가 필요하므로 제외한다.
- 검증: 빈 MySQL의 V1→V2와 Hibernate식 이름·FULLTEXT 누락 fixture의 baseline 1→V2를 Testcontainers에서 검증했다. 잘못된 canonical index와 `ON DELETE CASCADE` FK는 migration 실패 후 구조 수정·repair·재실행되는 negative test로 고정했다. 기존 로컬 DB에도 V2를 적용해 5개 FK와 주요 인덱스 이름, `FULLTEXT(title, description)`을 canonical 구조로 통일했으며 전후 행 수 `article 9853 / source 673 / users 1 / scrap 1 / chat_history 4`가 동일하다. 전체 49개 테스트가 약 1분 10초에 통과했다.

### #206 - develop PR Gradle·Testcontainers 자동 테스트 및 배포 workflow 분리

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/206
- 작업 브랜치: `ci/#206-backend-test-workflow`
- 상태: `Done` ([PR #207](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/207))
- 기준선: 기존 GitHub Actions는 `main` build/deploy에 결합되어 있고 `./gradlew clean build -x test`로 테스트를 제외해 `develop` PR의 45개 회귀 테스트가 자동 실행되지 않는다.
- 목표: secrets 없는 독립 CI에서 JDK 17과 `./gradlew test`를 사용해 단위·보안·Testcontainers MySQL 테스트를 자동 실행한다.
- overengineering 판단: 새 배포 파이프라인과 branch protection을 설계하지 않는다. 독립 CI를 추가하고 대상 EC2가 삭제된 legacy CD workflow는 제거하되 Dockerfile·Compose는 유지한다.
- 검증: 로컬에서 `./gradlew test --rerun-tasks`로 Testcontainers를 포함한 전체 45개 테스트가 1분 11초에 통과했다. PR #207의 첫 GitHub `Backend CI`도 운영 secret 없이 1분 50초에 통과했다.

### #204 - SSE query token 허용 경로 제한

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/204
- 작업 브랜치: `security/#204-sse-query-token-scope`
- 상태: `Done` ([PR #205](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/205))
- 기준선: `JwtAuthenticationFilter`가 EventSource 지원을 위해 모든 요청에서 `token` 쿼리 파라미터를 JWT로 해석해, SSE와 무관한 URL까지 인증정보 노출 범위가 넓다.
- 목표: Bearer JWT 동작은 유지하면서 query JWT를 로그인 사용자의 대화 저장에 인증 정보가 필요한 `GET /api/ai/{id}/ask`에서만 허용한다.
- overengineering 판단: 인증 모델, JWT 구조, OAuth redirect, SSE 구현은 바꾸지 않는다. 기존 필터의 토큰 추출 조건과 회귀 테스트만 다루는 작은 보안 변경이 적절하다.
- 검증: ask SSE query token 허용, 일반 API·summary SSE·비-GET ask의 query token 무시, 모든 경로의 Bearer JWT 유지 및 우선순위를 자동 테스트했다. 집중 보안 테스트와 Testcontainers를 포함한 전체 45개 테스트가 통과했다.

### #202 - MySQL Testcontainers 통합 테스트 및 트랜잭션 회귀 검증

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/202
- 작업 브랜치: `test/#202-mysql-integration`
- 상태: `Done` ([PR #203](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/203))
- 주요 파일:
  - `build.gradle`
  - `domain/article/repository/ArticleRepositoryIntegrationTest.java`
  - `docs/backend-improvement/mysql-testcontainers-integration.md`
- 기준선: 기존 자동 테스트는 mock/WebMvc 단위에 집중되어 #198 원자 UPDATE의 실제 MySQL 동시성·rollback 동작을 자동 회귀 검증하지 못했다.
- 목표: 테스트 전용 MySQL 8과 Repository slice에서 20개 동시 증가 및 예외 rollback을 검증하고 개발 DB와 테스트 데이터를 분리한다.
- overengineering 판단: 전체 SpringBootTest, API E2E, Redis/Kafka는 제외한다. MySQL 고유 트랜잭션 동작만 실제 DB로 검증하는 작은 slice가 적절하다.
- 검증: 동시 UPDATE 20건 후 viewCount 20, 강제 예외 transaction 후 viewCount 0, 종료 후 임시 MySQL/Ryuk 자동 제거, compose DB 비변경을 확인했다.

### #200 - 보호 API matcher 순서 수정 및 사용자 데이터 접근 통제 테스트

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/200
- 상태: `Done` ([PR #201](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/201))
- 검증 결과: 보호 API의 미인증·invalid JWT는 401, valid JWT principal은 사용자 소유 서비스에 전달되고 공개 API는 비로그인 접근을 유지했다.

### #198 - 기사 상세 동시 조회 viewCount lost update 원자 증가 개선

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/198
- 상태: `Done` ([PR #199](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/199))
- 검증 결과: 20개 성공 요청의 조회수 증가량이 개선 전 2건에서 DB 원자 UPDATE 적용 후 20건으로 일치했다.

### #196 - RSS/News API 수집 중복 방지와 재실행 안전성 개선

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/196
- 상태: `Done` ([PR #197](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/197))
- 검증 결과: 응답 내부 URL 중복과 동일 응답의 단일 인스턴스 순차 재실행을 자동 테스트로 고정했다.

### #194 - Mixed API single-instance saturation boundary

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/194
- 상태: `Done` ([PR #195](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/195))
- 검증 결과: 120.07 business RPS까지 dropped/error 없이 처리량이 증가했으며, 100 RPS부터 MySQL CPU와 Hikari pending 증가로 DB 자원 압박이 확인됐다.

### #192 - Mixed API constant-arrival-rate single-instance baseline

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/192
- 작업 브랜치: `perf/#192-mixed-arrival-rate-baseline`
- 상태: `Done` ([PR #193](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/193))
- 주요 파일:
  - `load-tests/k6/mixed-arrival-rate.js`
  - `docs/backend-improvement/mixed-arrival-rate-baseline.md`
- 목표: 한 로컬 Spring Boot 인스턴스에 합성 API 트래픽 `20 -> 40 -> 60 RPS`를 실행하고 endpoint별 p95/실패/달성 RPS를 Tomcat, Hikari, AI executor 지표와 함께 기록한다.
- overengineering 판단: 기존 k6, mock Gemini, Actuator만 재사용한다. 실제 외부 API, OAuth/scrap, 다중 인스턴스 추정, 측정 전 설정 튜닝 및 #110은 제외한다.
- 안전 조건: Redis 번역 캐시 사전 주입, summary 저장 비활성화, 기사 summary/view count 백업 및 복원, 예상 외 오류·dropped iteration·로컬 자원 압박 발생 시 다음 단계를 중단한다.
- Reviewer: 실행 가능한 k6 시나리오와 안전 장치가 변경되므로 PR diff 검토가 필요하다.
- 검증 결과:
  - 20/40/60 target RPS에서 business achieved RPS는 각각 20.13/40.17/60.13이었고 dropped iteration과 실패는 모두 0이었다.
  - 60 RPS에서도 조회 API p95는 최대 81.59ms, summary accepted p95는 3.07초였다.
  - Tomcat busy 최대 6/10 current, Hikari active/pending 최대 5/0, executor active/queued 최대 9/0으로 관측 범위에서 포화되지 않았다.
  - 60 RPS MySQL CPU point sample은 최대 54.41%였지만 p95 상승이나 connection pending이 없어 즉시 DB 튜닝 근거로 사용하지 않는다.
  - 테스트 기사 summary/view count를 원복하고 임시 DB backup table, Redis 테스트 key, backend/mock 프로세스를 제거했다.

## 1. 운영 원칙

### 기본 개발 흐름

모든 작업은 아래 순서로 진행한다.

```text
Issue 생성
→ 작업 브랜치 생성
→ 조사
→ 계획 제안
→ 사용자 승인
→ 구현
→ 테스트/검증
→ PR 생성
→ AI Reviewer 검토 또는 사용자 직접 확인
→ Blocking 반영 또는 사용자 확인
→ 최종 Blocking 없음 확인
→ 사용자 승인 후 merge
→ GitHub PR/Issue 상태 확인
```

### 역할 분리

현재는 VS Code Codex extension의 대화창 기반으로 역할을 분리한다.

| 역할 | 수행 위치 | 책임 |
| --- | --- | --- |
| Implementer / Planner | 현재 Codex 대화창 | 조사, 계획, 구현, 테스트, PR 생성, 리뷰 반영 |
| Reviewer | 별도 Codex 대화창 | PR diff 검토, Blocking/Non-blocking 분류, PR comment 작성 |
| User | 사용자 | 승인 게이트, 범위 판단, merge 승인 |

Reviewer는 코드 수정, 커밋, push, merge를 하지 않는다.
Reviewer는 `## AI Reviewer 검토 결과` 제목으로 GitHub PR comment를 남긴다.
Implementer는 새 Issue/PR을 만든 뒤 Reviewer Agent에게 전달할 검토 요청 예시를 사용자에게 함께 안내한다.
최신 Reviewer comment가 `MERGE_READY`이고 사용자가 해당 PR에 대해 명시적으로 merge 진행을 승인한 경우, Implementer는 PR을 merge한 뒤 GitHub PR/Issue 상태와 local `develop` 최신화로 merge 결과를 확인한다.
다만 develop commit history를 이슈별 핵심 변경 중심으로 유지하기 위해, 작업 내용과 관련 docs 기록은 가능한 한 PR 본 작업 커밋에 함께 포함한다.
merge 후 `WORK_PROGRESS.md`만 갱신하는 후처리 커밋은 기본값으로 만들지 않고, GitHub PR/Issue 상태로 merge 결과를 확인한다.
후처리 커밋은 PR에 포함된 문서가 다음 세션을 잘못 안내하거나 Reviewer가 명시적으로 요구한 경우처럼 필요한 때에만 만든다.
운영 코드, DB schema/index, API 응답, Repository query, Redis/cache 정책, k6/script 변경이 없는 순수 측정 결과/판단 문서 PR은 Reviewer를 생략하고 사용자 직접 확인 후 merge할 수 있다.
코드, 스크립트, DB, cache 정책 변경이 있거나 민감 정보 노출 위험이 있으면 Reviewer 검토를 받는다.

### 커밋 메시지 규칙

Conventional Commit type은 영어로 쓰고, 설명은 한국어로 작성한다.

예시:

```text
docs: AI Reviewer PR Comment Workflow 문서화
perf: 주요 API 부하 테스트 기준선 추가
fix: 검색 API LazyInitializationException 수정
chore: AI Reviewer Blocking 확인 스크립트 추가
```

### PR 리뷰 판정 기준

- `Blocking`: merge 전에 반드시 수정해야 하는 문제
- `Non-blocking`: 이번 PR에서 선택적으로 반영하거나 후속 이슈로 분리 가능한 개선점

Blocking 예시:

- 테스트 실패 또는 컴파일 오류
- 보안/민감 정보 로그 노출
- API 응답 구조의 의도치 않은 변경
- DB 데이터 손상 가능성
- Issue 범위를 벗어난 변경
- 문서에 명시한 원칙과 코드가 충돌

## 2. 지금까지 완료한 작업

### 현재까지의 큰 흐름 요약

- #113/#114에서 백엔드 개선 backlog와 AI 협업 운영 규칙을 만들었다.
- #115/#116에서 주요 API의 성능 관측 로그를 추가했고, 민감 정보 로그 노출은 Reviewer Blocking으로 잡아 같은 PR에서 수정했다.
- #117/#118, #119/#120에서 Reviewer comment workflow와 Blocking 확인 스크립트를 정리했다.
- #121/#122에서 k6 기반 주요 API 부하 테스트 기준선을 만들었고, 이 과정에서 `/api/search` 500 응답을 발견했다.
- #123/#126에서 검색 API `LazyInitializationException`을 수정하고, 동일 k6 smoke 조건에서 `http_req_failed`를 16.25%에서 0.00%로 낮췄다.
- #127/#128에서 Perspectives API cold/warm Redis cache 부하 테스트를 분리해 cold p95 218.65ms, warm p95 21.29ms를 기록했다.
- #129/#130에서 Perspectives Redis cache key/TTL/fallback/stale 허용 기준을 문서화했다.
- #131/#132에서 Perspectives FULLTEXT 쿼리의 `EXPLAIN`/`EXPLAIN ANALYZE`를 기록했고, 현재 데이터 규모에서는 FULLTEXT 인덱스 사용을 확인했다.
- #133/#134에서 검색 API FULLTEXT 실행 계획을 분석하고, 원문/번역 검색어가 같은 경우 중복 `OR MATCH`를 제거해 FULLTEXT 인덱스를 사용하도록 개선했다.
- #137/#138에서 기사 원문 크롤링 timeout/fallback과 외부 I/O 트랜잭션 분리를 개선했다.
- #140/#141에서 Perspectives API 캐시 hit/miss, Redis 실패, 번역 fallback 흐름을 회귀 테스트로 고정했다.
- #142/#143에서 Perspectives 다국어 이슈 매칭 품질 기준선을 정의해, Elasticsearch/Vector DB/RAG 같은 기술 도입 전에 현재 FULLTEXT 기반 매칭의 한계를 측정 가능하게 만들었다.
- #144/#145에서 Reviewer `MERGE_READY` 이후 PR별 명시 승인에 따라 바로 merge하고, Issue/PR 생성 시 Reviewer 요청 예시를 함께 안내하는 운영 흐름을 문서화했다.
- #146/#147에서 #142 기준선에 이어 로컬 개발 DB의 대표 샘플 후보와 MySQL FULLTEXT 매칭 스냅샷을 기록했다.
- #148/#149에서 `KeywordExtractor` 현행 정책을 회귀 테스트로 고정하고, 의미 유사도 개선이 아니라 키워드 후보 탐색 정책임을 명확히 남겼다.
- #150/#151에서 `KeywordExtractor.extract()`의 MySQL FULLTEXT BOOLEAN MODE 검색어 공백 포맷을 정규화했다.
- #152/#153에서 `First`, `round`, `Entre` 같은 샘플 기반 일반 토큰을 필터링해 Perspectives 후보 검색어 품질을 개선했다.
- #154/#155에서 RSS/News API 수집 편차와 갱신 주기가 FULLTEXT/향후 연관도 측정 해석에 주는 한계를 별도 LOG로 남겼다.
- #156/#157에서 #152 전후 키워드로 대표 샘플의 MySQL FULLTEXT 결과 변화를 측정했다.
- #158/#159에서 긴 `WORK_PROGRESS.md`를 보완하기 위한 다음 세션 handoff 문서를 정리했다.
- #160/#161에서 Perspectives FULLTEXT 정렬 기준을 latest-first, relevance-first, hybrid ordering으로 비교했다.
- #162/#163에서 새 이슈 후보마다 overengineering 여부를 먼저 판단하는 docs guardrail을 추가했다.
- #164/#165에서 #160 측정 결과를 바탕으로 Perspectives ranking policy 도입 보류와 hybrid 후보 적용 기준을 docs/ADR로 정리했다.
- #166/#167에서 PR 본 작업 커밋에 docs 기록을 함께 포함하고 merge 후 후처리 커밋을 기본값으로 만들지 않는 기준을 정리했다.
- #168/#169에서 검색 API FULLTEXT 검색어별 성능 기준선을 수립했다.
- #170/#171에서 기사 원문 크롤링 동기 외부 호출 응답 지연 기준선을 수립했다.
- #172/#173에서 기사 요약 API 외부 호출 단계별 latency 로그를 추가했다.
- #174/#175에서 주요 기사 조회 API 고부하 부하 테스트 및 DB 병목 기준선을 수립했다.
- #176/#177에서 `popular` 기사 조회의 `Using filesort`가 현재 데이터 규모에서 인덱스 추가가 필요한 병목인지 k6와 `EXPLAIN ANALYZE`로 판단했다.
- #178/#179에서 로컬 부하 테스트 시 애플리케이션 latency 로그와 Docker/local resource 지표를 같은 실행 구간에 캡처하는 관측 runbook을 정리했다.
- #180/#181에서 주요 조회 API 단일 인스턴스 TPS 한계와 포화 신호 구간을 정리했다.
- #182/#183에서 Gemini mock latency 기반 외부 호출 병목 기준선을 수립하고 merge를 완료했다.
- #184에서 Gemini summary timeout 상한과 upstream 502/504 오류 분리를 진행 중이다.
- 현재 반복 성능/안정성 기본기 흐름의 주요 후보(#123, #127, #129, #131, #133, #137, #140, #142, #146)와 AI workflow 보강(#144)은 merge 완료 상태다.

### #113 / PR #114 - 백엔드 개선 Backlog 및 AI 작업 운영 규칙 수립

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/113
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/114
- 상태: merged
- 주요 파일:
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/ai-workflow/README.md`

작업 내용:

- 백엔드 개선 작업을 Issue, Branch, PR, Merge 흐름으로 관리하도록 문서화했다.
- AI를 단순 코드 생성 도구가 아니라 조사자, 계획자, 구현자, 검토자로 역할 분리해 사용하는 규칙을 정리했다.
- 사용자 승인 전에는 코드, 설정, DB, 배포 작업을 하지 않는 승인 게이트를 명시했다.

의미:

- AI 작업을 통제 가능한 개발 프로세스 안에 넣는 첫 단계다.
- 이후 모든 개선 작업의 기준 문서가 되었다.

---

### #115 / PR #116 - 주요 API 성능 관측 로그 추가

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/115
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/116
- 상태: merged
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/domain/article/service/ArticleService.java`
  - `src/main/java/com/example/globalTimes_be/domain/article/service/ExploreService.java`
  - `src/main/java/com/example/globalTimes_be/domain/detail/service/PerspectivesService.java`
  - `src/main/java/com/example/globalTimes_be/domain/search/service/SearchArticlesService.java`
  - `src/main/java/com/example/globalTimes_be/domain/search/service/TranslationService.java`
  - `src/main/java/com/example/globalTimes_be/global/translate/TranslateUtil.java`
  - `docs/backend-improvement/performance-observability-baseline.md`

작업 내용:

- 주요 API의 처리 시간과 병목 후보를 로그로 확인할 수 있도록 관측 로그를 추가했다.
- `PerspectivesService`에 캐시 hit/miss, 기준 기사 조회 시간, 키워드 추출 시간, 번역 여부, FULLTEXT 검색 시간, 국가/기사 수, 전체 처리 시간을 기록했다.
- `SearchArticlesService`, `TranslationService`, `ArticleService`, `ExploreService`에 DB 조회 시간, 결과 수, 번역 캐시 여부, 외부 번역 API 호출 시간 등을 기록했다.
- 검색어와 번역 결과 원문은 로그에 남기지 않고 길이만 기록하도록 수정했다.

AI Reviewer로 발견한 문제:

- 처음에는 `plainKeyword`, `translated` 원문이 info 로그에 남아 있었다.
- `TranslateUtil`에도 원문/번역 결과 로그가 남아 있었다.
- Reviewer가 이를 Blocking으로 지적했고, 같은 PR에서 수정했다.

검증:

```text
./gradlew.bat test
git diff --check
```

의미:

- 성능 개선을 감으로 말하지 않고, 이후 p95/오류율/캐시 hit 여부와 연결해 분석할 기반을 만들었다.
- 민감 정보 로그 노출을 Reviewer가 잡아내고 수정한 사례가 남았다.

---

### #117 / PR #118 - AI Reviewer PR Comment Workflow 문서화

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/117
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/118
- 상태: merged
- 주요 파일:
  - `docs/ai-workflow/reviewer-comment-workflow.md`
  - `docs/ai-workflow/README.md`

작업 내용:

- Implementer 세션과 Reviewer 세션 사이에서 사용자가 리뷰 결과를 매번 복사하는 부담을 줄이기 위해, GitHub PR comment를 중심으로 리뷰 결과와 반영 내역을 기록하는 반자동 흐름을 문서화했다.
- Reviewer는 PR diff 검토와 PR comment 작성만 수행하고, 코드 수정/커밋/push/merge는 금지하도록 명확히 했다.

의미:

- 완전 자동화가 아니라, 먼저 GitHub PR을 AI 협업의 단일 기록 저장소로 표준화한 단계다.
- 이후 스크립트/MCP 자동화의 문제 정의가 되었다.

---

### #119 / PR #120 - AI Reviewer Blocking 확인 스크립트 추가

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/119
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/120
- 상태: merged
- 주요 파일:
  - `scripts/ai-workflow/check-review-blocking.ps1`
  - `docs/ai-workflow/review-blocking-check-script.md`
  - `docs/ai-workflow/reviewer-comment-workflow.md`

작업 내용:

- 최신 AI Reviewer comment에서 `Blocking`, `Non-blocking`, `결론` 섹션을 추출하는 PowerShell 스크립트를 추가했다.
- 결과를 아래 상태로 출력한다.

| 상태 | 의미 |
| --- | --- |
| `MERGE_READY` | Blocking 없음 |
| `CHANGES_REQUIRED` | Blocking 있음 |
| `REVIEW_NOT_FOUND` | AI Reviewer comment 또는 Blocking 섹션을 찾지 못함 |

사용 예시:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\ai-workflow\check-review-blocking.ps1 -PrNumber 122
```

Reviewer가 잡아낸 문제와 반영:

- Implementer comment를 Reviewer comment로 오탐할 수 있는 문제를 발견했다.
- `## AI Reviewer 검토 결과` 제목 형식만 Reviewer comment 후보로 잡도록 강화했다.
- `### Blocking` 섹션이 없으면 안전하게 `REVIEW_NOT_FOUND`로 실패하도록 수정했다.
- GitHub comment 앞 BOM/공백을 허용했다.
- Windows PowerShell 5.1의 UTF-8 파싱 차이를 고려해 일부 한글 판정을 유니코드 코드포인트 기반으로 처리했다.

의미:

- AI Reviewer comment를 사람이 일일이 읽지 않고, merge 전 상태를 빠르게 확인할 수 있게 했다.
- MCP 서버로 가기 전 단계의 작은 자동화다.

---

### #121 / PR #122 - 주요 API 부하 테스트 기준선 측정

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/121
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/122
- 상태: merged
- 주요 파일:
  - `load-tests/k6/api-baseline.js`
  - `docs/backend-improvement/load-test-baseline.md`
  - `docs/backend-improvement/BACKLOG.md`

작업 내용:

- k6 기반 주요 API 부하 테스트 시나리오를 추가했다.
- 대상 API:
  - `GET /api/articles/latest`
  - `GET /api/articles/cursor`
  - `GET /api/articles/popular`
  - `GET /api/articles/explore`
  - `GET /api/search`
  - `GET /api/news/{id}/perspectives`
- 목적은 성능 개선 자체가 아니라 개선 전 기준선 측정이다.
- 과다 트래픽 대응은 이번 PR에서 구현하지 않고, rate limit, cache, 비동기 처리 등 후속 개선 후보로 분리했다.

k6 설치 및 검증:

- k6 설치 경로: `C:\Program Files\k6\k6.exe`
- 버전: `k6.exe v2.1.0`
- 검증:

```powershell
node --check load-tests/k6/api-baseline.js
& "C:\Program Files\k6\k6.exe" inspect .\load-tests\k6\api-baseline.js
git diff --check
```

Smoke test 실행 조건:

```text
BASE_URL=http://localhost:8080
ARTICLE_ID=8449
SEARCH_TEXT=대구
ARTICLES_VUS=1
SEARCH_VUS=1
PERSPECTIVES_VUS=1
ARTICLES_DURATION=15s
SEARCH_DURATION=15s
PERSPECTIVES_DURATION=15s
```

Smoke test 결과:

| API/시나리오 | 평균 응답 시간 | p95 응답 시간 | 오류율 | 비고 |
| --- | --- | --- | --- | --- |
| 전체 | 28.38ms | 49.29ms | 0.00% | 86 requests |
| articles | 28.9ms | 48.84ms | 0.00% | latest, cursor, popular, explore |
| search | 41.77ms | 61.1ms | 0.00% | `SEARCH_TEXT=대구` |
| perspectives | 13.05ms | 15.86ms | 0.00% | 반복 호출 기준 |

추가 발견:

- `SEARCH_TEXT=war`, `korea`, `economy`, `technology` 요청 시 `/api/search`에서 500 응답 발생
- 서버 로그에서 `LazyInitializationException` 확인
- 해당 문제는 부하 테스트 스크립트 문제가 아니라 검색 API 버그로 판단
- 후속 이슈 #123으로 분리

의미:

- 단순히 “부하 테스트를 했다”가 아니라, 부하 테스트 과정에서 실제 API 실패 케이스를 발견했다.
- 이후 #123에서 수정 후 동일 k6 smoke test를 재실행하면 오류율 개선을 수치로 기록할 수 있다.

## 3. 현재 바로 이어서 해야 할 작업

### #123 / PR #126 - 검색 API LazyInitializationException으로 인한 500 응답 수정

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/123
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/126
- 상태: merged
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/domain/search/service/SearchArticlesService.java`

문제:

- `/api/search?text=war`, `korea`, `economy`, `technology` 요청에서 500 응답이 발생했다.
- 서버 로그에서 `LazyInitializationException`이 확인되었다.
- `Article.source`가 `FetchType.LAZY`이고 `spring.jpa.open-in-view=false`인 상태에서, 검색 결과 DTO 변환 중 `article.getSource().getSourceName()` 접근이 영속성 컨텍스트 밖에서 발생할 수 있었다.

수정:

- `SearchArticlesService#getSearchArticles()`에 `@Transactional(readOnly = true)`를 추가했다.
- 검색 쿼리와 응답 DTO 구조는 변경하지 않았다.
- N+1, fetch join, DTO projection 개선은 이번 PR에 섞지 않고 후속 성능 이슈 후보로 남겼다.

검증:

```text
./gradlew.bat test
git diff --check
/api/search?text=war -> 200
/api/search?text=korea -> 200
/api/search?text=economy -> 200
/api/search?text=technology -> 200
```

k6 smoke test:

```text
BASE_URL=http://localhost:8080
ARTICLE_ID=8449
SEARCH_TEXT=war
ARTICLES_VUS=1
SEARCH_VUS=1
PERSPECTIVES_VUS=1
ARTICLES_DURATION=15s
SEARCH_DURATION=15s
PERSPECTIVES_DURATION=15s
```

결과:

| API/시나리오 | p95 응답 시간 | 오류율 | 비고 |
| --- | --- | --- | --- |
| 전체 | 63.9ms | 0.00% | 86 requests |
| articles | 45.21ms | 0.00% | latest, cursor, popular, explore |
| search | 64.12ms | 0.00% | `SEARCH_TEXT=war` |
| perspectives | 59.97ms | 0.00% | 반복 호출 기준 |

의미:

- #121/#122 smoke 기준 `SEARCH_TEXT=war` 조건에서 기록된 `http_req_failed=16.25%`가 #123 수정 후 `0.00%`로 개선되었다.
- 부하 테스트 과정에서 발견한 실제 API 버그를 수정하고, 동일 조건으로 재측정해 개선을 수치로 남겼다.

---

### #127 - Perspectives API cold/warm cache 부하 테스트 분리

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/127
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/128
- 상태: merged
- 작업 브랜치: `perf/#127-perspectives-cache-load-test`

목표:

- 기존 `api-baseline.js`의 `perspectives_repeated`는 Redis cache miss 첫 요청과 cache hit 반복 요청을 하나의 평균/p95에 섞어 기록한다.
- `GET /api/news/{id}/perspectives`의 cold cache와 warm cache를 분리 측정해 Redis 캐시가 반복 조회 성능을 얼마나 개선하는지 수치화한다.

현재 확인한 구조:

- Redis key: `perspectives:article:{articleId}`
- TTL: `perspectives.cache-ttl-seconds`, 기본 3600초
- cache hit 로그: `cacheHit=true`, `cacheReadMs`, `totalMs`, `countriesFound`, `totalArticles`
- cache miss 로그: `cacheHit=false`, `baseLookupMs`, `keywordMs`, `translationMs`, `translatedSearchMs`, `originalSearchMs`, `totalMs`

작업 범위:

- Perspectives 전용 k6 스크립트 추가
- cold/warm cache 측정 조건 문서화
- 측정 결과 기록 템플릿 추가
- 실제 Redis TTL/key 정책 변경, 로컬 캐시 도입, FULLTEXT 쿼리 개선, semantic similarity 개선은 후속 이슈로 분리

초기 smoke 측정:

```text
BASE_URL=http://localhost:8080
ARTICLE_IDS=8449
WARM_ARTICLE_ID=8449
COLD_VUS=1
COLD_ITERATIONS=1
WARM_VUS=1
WARM_DURATION=15s
```

실행 전 Redis key `perspectives:article:8449`를 삭제했다.

| API/시나리오 | 평균 응답 시간 | p95 응답 시간 | 오류율 | 비고 |
| --- | --- | --- | --- | --- |
| 전체 | 30.84ms | 71.49ms | 0.00% | 16 requests |
| cold cache | 218.65ms | 218.65ms | 0.00% | 1 request |
| warm cache | 18.32ms | 21.29ms | 0.00% | 15 requests |

의미:

- 동일 articleId 기준 warm cache p95가 cold cache p95보다 크게 낮았다.
- Redis cache hit가 반복 조회 응답 시간을 줄인다는 초기 근거를 확보했다.
- cold cache는 1회 샘플이므로, 더 안정적인 비교가 필요하면 여러 articleId 대상으로 반복 측정한다.

주의:

- 현재 Perspectives 관련 기사 탐색은 제목 기반 키워드 추출과 MySQL FULLTEXT 검색에 의존한다.
- 이는 진정한 의미의 semantic similarity가 아니므로, 같은 사건이라도 표현이 다르면 누락될 수 있다.
- 이번 작업은 검색 품질 개선이 아니라 cache hit/miss 성능 차이를 분리 측정하는 작업이다.

---

### #129 - Perspectives API Redis 캐시 정책 및 정합성 점검

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/129
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/130
- 상태: merged
- 작업 브랜치: `perf/#129-perspectives-redis-cache-policy`
- 주요 파일:
  - `docs/backend-improvement/perspectives-redis-cache-policy.md`

목표:

- #127에서 확인한 Redis warm cache 효과를 바탕으로, `PerspectivesService`의 cache key, TTL, 실패 처리, stale cache 허용 기준을 설명 가능하게 정리한다.
- Redis를 단순 성능 도구로만 쓰는 것이 아니라, 캐시 정합성 trade-off까지 문서화한다.

현재 확인한 정책:

- Redis key: `perspectives:article:{articleId}`
- TTL: `perspectives.cache-ttl-seconds`, 기본 3600초
- `perspectives.cache-ttl-seconds=0`이면 캐시 미사용
- Redis read 실패, JSON 역직렬화 실패는 warn 로그 후 DB/FULLTEXT 재계산
- Redis write 실패는 warn 로그 후 무시하고 API 응답은 반환
- 명시적 invalidation은 현재 없음

정합성 판단:

- 현재 서비스는 뉴스 수집 후 읽기 중심이며, 기사 제목/국가/category/source를 수정하는 API는 없다.
- `viewCount`, `summary`, `crawledContent` 변경은 Perspectives 응답에 포함되지 않아 무효화 대상이 아니다.
- 새 관련 기사 삽입, base article title 변경, country/category/source 변경은 stale cache 가능성이 있지만 현재는 1시간 TTL 기반 stale 허용으로 충분하다고 판단한다.
- 명시적 invalidation은 관리자 수정 API나 더 긴 TTL이 필요해질 때 별도 이슈로 검토한다.

---

### #131 - Perspectives API FULLTEXT 쿼리 EXPLAIN 분석

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/131
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/132
- 상태: merged
- 작업 브랜치: `perf/#131-perspectives-fulltext-explain`
- 주요 파일:
  - `docs/backend-improvement/perspectives-fulltext-explain.md`

목표:

- #127에서 cold cache 비용이 확인되었고 #129에서 Redis 정책을 정리했으므로, cache miss 경로의 DB FULLTEXT 쿼리가 의도한 인덱스를 사용하는지 MySQL `EXPLAIN`으로 확인한다.
- 캐시를 쓰는 이유뿐 아니라 캐시가 비었을 때 DB 쿼리가 어떤 실행 계획으로 동작하는지도 설명 가능하게 만든다.

조사 결과:

- 대상 쿼리: `ArticleRepository.findPerspectives`
- 대상 테이블: `article`
- 로컬 데이터 수: 9853 rows
- FULLTEXT 인덱스: `ft_article_title_description(title, description)`
- 대표 쿼리의 `EXPLAIN` 결과: `type=fulltext`, `key=ft_article_title_description`
- `ORDER BY published_at DESC` 때문에 `Using filesort`가 표시된다.
- `EXPLAIN ANALYZE` 기준 `+war` 404건 매칭은 정렬 포함 약 10.3ms, `+economy` 39건 매칭은 약 1.37ms였다.

판단:

- 현재 데이터 규모에서는 `findPerspectives` 쿼리가 FULLTEXT 인덱스를 사용하며, 즉시 쿼리/인덱스 변경이 필요한 병목으로 보이지 않는다.
- `Using filesort`는 매칭 결과가 커질 때 p95에 영향을 줄 수 있으므로 후속 관찰 포인트로 남긴다.
- 검색 품질, 다국어 매칭, semantic similarity는 별도 품질 개선 이슈로 분리한다.

---

### #133 - 검색 API FULLTEXT 성능 및 검색어별 안정성 분석

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/133
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/134
- 상태: merged
- 작업 브랜치: `perf/#133-search-fulltext-analysis`
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/domain/article/repository/ArticleRepository.java`
  - `src/main/java/com/example/globalTimes_be/domain/search/service/SearchArticlesService.java`
  - `docs/backend-improvement/search-fulltext-analysis.md`

목표:

- `/api/search`가 검색어별로 어떤 FULLTEXT 실행 계획을 사용하는지 확인한다.
- #123에서 수정한 영어 검색어 500 문제 이후, 영어/한국어/다국어 검색어별 매칭 수와 실행 계획 차이를 정리한다.

조사 결과:

- 검색 API는 원문 검색어와 번역 검색어를 `OR MATCH`로 묶어 검색한다.
- `war`, `economy`, `technology`, `대구` API 호출은 모두 200 응답을 반환했다.
- 로컬 DB 기준 FULLTEXT 매칭 수는 `war=404`, `technology=82`, `economy=39`, `대구 OR daegu=0`이었다.
- 단일 MATCH 쿼리는 `ft_article_title_description` FULLTEXT 인덱스를 사용했다.
- 원문과 번역어가 같은 영어 검색어에서도 기존 쿼리는 중복 `OR MATCH`가 되어 MySQL이 `idx_article_published_at` 역방향 스캔을 선택했다.

수정:

- `text`와 `translatedText`가 trim/case-insensitive 기준으로 같으면 단일 MATCH 쿼리를 사용하도록 분기했다.
- 원문과 번역어가 다르면 기존 OR MATCH 쿼리를 유지한다.
- API 응답 구조는 변경하지 않았다.

판단:

- 영어처럼 원문/번역어가 같은 검색어에서는 중복 OR를 제거해 의도한 FULLTEXT 인덱스를 사용하도록 하는 것이 작고 안전한 개선이다.
- 한국어/다국어 검색 품질, 형태소 분석, Elasticsearch/vector search는 별도 품질 개선 이슈로 분리한다.

검증:

```text
./gradlew.bat test
git diff --check
/api/search?text=war -> 200
/api/search?text=economy -> 200
/api/search?text=technology -> 200
/api/search?text=대구 -> 200
```

Reviewer 결과:

- Blocking 없음.
- Non-blocking으로 지적된 `EXPLAIN` 예시의 필터 조건 범위 설명은 같은 PR에서 보강했다.
- 2026-07-03 기준 PR #134는 merge 완료되었다.

---

### #137 - 기사 원문 크롤링 timeout 및 실패 처리 개선

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/137
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/138
- 상태: merged
- 작업 브랜치: `fix/#137-crawling-timeout-fallback`
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/global/crawler/ArticleCrawler.java`
  - `src/main/java/com/example/globalTimes_be/domain/detail/service/DetailService.java`
  - `src/main/java/com/example/globalTimes_be/domain/detail/service/ArticleCrawlContentService.java`
  - `src/main/java/com/example/globalTimes_be/domain/trend/service/TrendCrawledService.java`

목표:

- AI 요약/질의응답 경로에서 외부 언론사 페이지 크롤링이 사용자 요청을 오래 붙잡지 않도록 timeout을 명시한다.
- 크롤링 실패 또는 본문 추출 실패 시 API별 기존 fallback/에러 정책이 예측 가능하게 동작하도록 한다.
- DB 조회/저장 트랜잭션과 외부 네트워크 I/O를 분리한다.

조사 결과:

- `DetailService#getArticleCrawledContent()`는 `@Transactional(readOnly = true)` 안에서 외부 크롤링과 `articleRepository.save()`를 함께 수행하고 있었다.
- `DetailService`와 `TrendCrawledService` 모두 `Jsoup.connect(...).get()`에 timeout/user-agent 설정이 없었다.
- `AiController#summarizeArticle()`에는 크롤링 실패 시 기사 서두를 제공하는 fallback 분기가 있었지만, 기존 서비스가 예외를 던져 해당 분기가 사실상 도달하기 어려웠다.

수정 방향:

- `ArticleCrawler` 공통 컴포넌트로 Jsoup timeout/user-agent/본문 추출을 모은다.
- `DetailService`는 크롤링 실패 시 `null`을 반환해 일반 summary API의 기존 fallback 분기를 살린다.
- SSE summary와 ask API는 기존처럼 `DetailErrorStatus._CRAWLER_ERROR`를 유지한다.
- `ArticleCrawlContentService`를 별도 서비스로 분리해 DB 조회/저장 트랜잭션이 Spring 프록시를 타도록 한다.
- 비동기/Kafka는 이번 PR 범위에서 제외하고, 동기 요청 경로의 timeout/fallback을 먼저 안정화한다.

검증:

```text
./gradlew.bat test
git diff --check
```

Reviewer 결과:

- `## AI Reviewer 검토 결과` 제목의 Reviewer comment 기준 Blocking 없음.
- Non-blocking: `ArticleCrawler` URL 포함 warn 로그는 추후 운영 로그 정책에 따라 도메인/articleId 중심으로 축소 검토 가능.
- Non-blocking: PR #136 merge 후 Issue #135가 open으로 남은 housekeeping 항목 확인.
- 2026-07-03 기준 PR #138은 merge 완료되었고, Issue #137은 closed 상태다.

---

### #140 - Perspectives API 캐시/실패 회귀 테스트 기반 마련

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/140
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/141
- 상태: merged
- 작업 브랜치: `test/#140-perspectives-regression-tests`
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/domain/detail/service/PerspectivesService.java`
  - `src/test/java/com/example/globalTimes_be/domain/detail/service/PerspectivesServiceTest.java`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- #127/#129/#131에서 측정/문서화한 Perspectives cache hit/miss, Redis 실패, 번역 fallback 흐름을 테스트로 고정한다.
- 이후 Redis, 검색, 번역, 유사도 개선을 이어갈 때 기존 동작이 깨졌는지 자동으로 확인할 안전망을 만든다.

조사 결과:

- `PerspectivesService`는 cache hit 시 Redis JSON을 `PerspectivesResDTO`로 역직렬화해 즉시 반환한다.
- Redis read 실패 또는 캐시 역직렬화 실패는 warn 로그 후 재계산한다.
- cache miss 시 기준 기사 조회, 키워드 추출, 비영어 기사 번역, FULLTEXT 검색, 국가별 grouping, Redis 저장 순서로 동작한다.
- 번역 실패는 원문 키워드 fallback으로 처리한다.
- Redis write 실패는 warn 로그 후 API 응답을 정상 반환한다.

수정 방향:

- Spring context를 띄우지 않는 mock 기반 `PerspectivesServiceTest`를 추가한다.
- cache hit, cache miss, Redis read 실패, 캐시 역직렬화 실패, Redis write 실패, 번역 실패 fallback 분기를 테스트로 고정한다.
- API 응답 구조와 운영 코드는 변경하지 않고 회귀 테스트 안전망을 우선 확보한다.

검증:

```text
./gradlew.bat test
git diff --check
```

초기 테스트 실패와 조정:

- 캐시 JSON 테스트에서 `LocalDateTime` 직렬화를 위해 테스트 `ObjectMapper`에 JavaTime module 등록이 필요했다.
- 비영어 번역 실패 테스트는 `KeywordExtractor`의 실제 plain/boolean keyword 추출 결과를 기준으로 기대값을 맞췄다.
- 서비스 코드는 변경하지 않고 테스트 코드의 fixture/기대값만 실제 동작에 맞게 조정했다.
- 2026-07-03 기준 PR #141은 merge 완료되었고, Issue #140은 closed 상태다.

---

### #142 - Perspectives 다국어 이슈 매칭 품질 기준선 정의

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/142
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/143
- 상태: merged
- 작업 브랜치: `perf/#142-perspectives-matching-baseline`
- 주요 파일:
  - `docs/backend-improvement/perspectives-matching-quality-baseline.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- Perspectives API가 현재 제목 키워드, 영어 번역, MySQL FULLTEXT 검색으로 다국어 이슈를 어떻게 매칭하는지 기준선을 정의한다.
- Elasticsearch, Vector DB, RAG, issue clustering 같은 기술 도입 전에 대표 샘플 기준으로 현재 매칭 품질과 한계를 측정할 수 있게 만든다.
- #110의 장기 troubleshooting 내용은 직접 구현하지 않고, 그중 P3 다국어 이슈 매칭 품질 개선의 첫 측정 단계만 분리해 진행한다.

조사 결과:

- `PerspectivesService`는 기준 기사 제목에서 `KeywordExtractor.extractPlain()`과 `KeywordExtractor.extract()`로 평문/BOOLEAN MODE 키워드를 만든다.
- 기준 기사 언어가 영어가 아니면 평문 키워드를 영어로 번역한 뒤 다시 BOOLEAN MODE 키워드로 변환해 검색한다.
- 번역 키워드와 원문 키워드가 다르면 `findPerspectives`를 한 번 더 호출해 결과를 병합한다.
- `ArticleRepository.findPerspectives`는 `MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE)`와 `ORDER BY published_at DESC LIMIT 50`을 사용한다.
- 결과는 국가별로 그룹핑되고 국가당 최대 3개 기사로 제한된다.

수정 방향:

- API 동작, DB 스키마, 검색 엔진, 캐시 정책은 변경하지 않는다.
- 현재 매칭 흐름, 알려진 한계, 대표 샘플 선정 기준, 측정 절차, 결과 기록 템플릿을 문서화한다.
- 기술 도입 후보는 기준선 측정 이후 ADR 또는 후속 이슈로 분리한다.

검증:

```text
git diff --check
```

Reviewer 결과:

- `## AI Reviewer 검토 결과` 제목의 Reviewer comment 기준 Blocking 없음.
- Non-blocking 없음.
- `check-review-blocking.ps1 -PrNumber 143` 결과 `MERGE_READY`를 확인했다.
- 2026-07-03 기준 PR #143은 merge 완료되었고, Issue #142는 closed 상태다.

---

### #144 - AI Reviewer MERGE_READY 이후 merge 흐름 문서화

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/144
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/145
- 상태: merged
- 작업 브랜치: `docs/#144-merge-ready-flow`
- 주요 파일:
  - `docs/ai-workflow/reviewer-comment-workflow.md`
  - `docs/ai-workflow/README.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- Reviewer comment에서 `MERGE_READY`와 Blocking 없음이 확인되고 사용자가 해당 PR에 대해 명시적으로 merge 진행을 승인한 경우, Implementer가 바로 merge까지 수행하는 흐름을 문서화한다.
- Reviewer comment 이후 PR diff에 추가 변경이 있으면 최신 diff 기준으로 재검토를 받아야 한다는 기준을 명확히 한다.
- 새 Issue/PR 생성 후에는 Reviewer Agent에게 전달할 검토 요청 예시를 사용자에게 함께 안내하는 규칙을 추가한다.
- 당시 기준으로는 merge 후 `WORK_PROGRESS.md`에 PR 상태, Reviewer 결정, merge 결과, 다음 작업 상태를 남기도록 했다. 이 기본값은 #166에서 PR 본 작업 커밋에 docs 기록을 포함하고 GitHub PR/Issue 상태로 merge 결과를 확인하는 방향으로 조정한다.

수정 방향:

- `reviewer-comment-workflow.md`에 MERGE_READY 이후 처리 기준을 추가한다.
- `docs/ai-workflow/README.md`에 merge 결과와 다음 작업 상태 기록 위치를 추가한다.
- `WORK_PROGRESS.md`에 #142/#143 merge 완료 상태와 #144 진행 상태를 기록한다.
- AI Reviewer Blocking 반영으로 merge 승인을 PR별 명시 승인으로 좁히고, Reviewer comment 이후 diff 변경 시 재검토 필수 기준을 추가한다.

검증:

```text
git diff --check
```

Reviewer 결과:

- 첫 Reviewer comment에서 Blocking 2건이 있었다.
- Blocking 1: merge 승인 조건이 포괄 승인으로 해석될 수 있어 PR별 명시 승인으로 좁히도록 지적했다.
- Blocking 2: Reviewer comment 이후 PR diff 변경 시 재검토 기준이 단정적이지 않아 최신 diff 기준 재검토 필수로 명확히 하도록 지적했다.
- 같은 PR에서 두 Blocking을 반영했다.
- 재검토 결과 `MERGE_READY`, Blocking 없음, Non-blocking 없음으로 확인했다.
- 2026-07-03 기준 PR #145는 merge 완료되었고, Issue #144는 closed 상태다.

---

### #146 - Perspectives 대표 샘플 FULLTEXT 매칭 스냅샷 기록

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/146
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/147
- 상태: merged
- 작업 브랜치: `perf/#146-perspectives-sample-snapshot`
- 주요 파일:
  - `docs/backend-improvement/perspectives-matching-sample-snapshot.md`
  - `docs/backend-improvement/perspectives-matching-quality-baseline.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- #142에서 정의한 Perspectives 매칭 품질 기준선에 실제 로컬 개발 DB 샘플 스냅샷을 추가한다.
- 외부 번역 API 호출 없이 MySQL FULLTEXT 원문 키워드 기준의 match count, 국가/언어 분포, 성공/실패 후보를 기록한다.
- FULLTEXT match count가 곧 품질을 의미하지 않는다는 점을 반환 예시와 함께 남긴다.

조사 결과:

- 로컬 개발 DB에는 2026-07-03 기준 `article` 9853건이 있다.
- `kr/ko/general`, `global/en/general`, `cn/zh/general`, `us/en/*`, `gb/en/*` 등 여러 국가/언어 샘플이 존재한다.
- `8146` US-Iran talks 샘플은 25건을 반환하지만, `First`, `round` 같은 일반 토큰 때문에 sports 등 약한 매칭이 섞였다.
- `8149` BTS 샘플은 BTS entity가 강해 여러 국가/언어에서 비교적 좋은 매칭을 반환했다.
- `8148`, `8468`, `8461`, `9440` 등은 현재 원문 FULLTEXT 조건에서 0건을 반환해 키워드 추출/언어/표현 차이 한계를 보여준다.
- `.env`의 민감 정보와 외부 API 응답 전문은 문서에 포함하지 않는다.

수정 방향:

- API 동작, DB 스키마, 검색 로직, Redis 정책은 변경하지 않는다.
- `perspectives-matching-sample-snapshot.md`에 측정 환경, SQL 형태, 샘플 요약, 반환 예시, 후속 후보를 기록한다.
- 전체 API 경로와 번역 포함 측정은 외부 API 사용 승인 후 별도 이슈로 분리한다.

검증:

```text
git diff --check
```

Reviewer 결과:

- `## AI Reviewer 검토 결과` 제목의 Reviewer comment 기준 Blocking 없음.
- Non-blocking 없음.
- `check-review-blocking.ps1 -PrNumber 147` 결과 `MERGE_READY`를 확인했다.
- 2026-07-03 기준 PR #147은 merge 완료되었고, Issue #146은 closed 상태다.

---

### #148 - KeywordExtractor 현행 정책 회귀 테스트 및 한계 명시

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/148
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/149
- 상태: merged
- 작업 브랜치: `test/#148-keyword-extractor-regression`
- 주요 파일:
  - `src/test/java/com/example/globalTimes_be/domain/detail/util/KeywordExtractorTest.java`
  - `docs/backend-improvement/perspectives-matching-sample-snapshot.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- #146 스냅샷에서 드러난 `First`, `round`, `Entre` 같은 일반 토큰 문제를 바로 수정하지 않고, 먼저 `KeywordExtractor`의 현재 정책을 테스트로 고정한다.
- 현재 정책이 의미 유사도 검색이 아니라 제목 토큰 기반 후보 탐색임을 문서에 명확히 남긴다.
- 이후 일반 토큰 필터링, entity-aware keyword extraction, ranking 개선을 검토할 때 비교 기준을 확보한다.

수정 방향:

- 운영 코드의 `KeywordExtractor` 정책은 변경하지 않는다.
- `First round`, `BTS`, `Trump-backed`, 한국어, 프랑스어 제목 샘플을 단위 테스트로 고정한다.
- 테스트 과정에서 현재 `extract()`의 BOOLEAN MODE 문자열 공백 형식과 Unicode ellipsis 기반 한국어 토큰 결합도 함께 드러난다.
- #146 스냅샷 문서에 수동 키워드 예시는 측정 보조 자료이며, 실제 Java 정책은 테스트로 고정한다는 설명을 추가한다.

검증:

```text
./gradlew.bat test
git diff --check
```

Reviewer 결과:

- `## AI Reviewer 검토 결과` 제목의 Reviewer comment 기준 Blocking 없음.
- Non-blocking 없음.
- `check-review-blocking.ps1 -PrNumber 149` 결과 `MERGE_READY`를 확인했다.
- 2026-07-03 기준 PR #149는 merge 완료되었고, Issue #148은 closed 상태다.

---

### #150 - KeywordExtractor BOOLEAN MODE 검색어 포맷 정규화

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/150
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/151
- 상태: merged
- 작업 브랜치: `fix/#150-keyword-boolean-format`
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/domain/detail/util/KeywordExtractor.java`
  - `src/test/java/com/example/globalTimes_be/domain/detail/util/KeywordExtractorTest.java`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- #148 회귀 테스트에서 드러난 `+First+round  Iran  talks` 형태의 불규칙한 BOOLEAN MODE 검색어 포맷을 `+First +round Iran talks`처럼 토큰 사이 공백이 명확한 형태로 정규화한다.
- 일반 토큰 필터링, entity-aware 추출, ranking 정책은 변경하지 않고 검색어 문자열 조립 결함만 좁게 수정한다.
- 이후 일반 토큰 필터링 개선을 검토할 때 비교 기준이 흔들리지 않도록 한다.

수정 방향:

- `KeywordExtractor.extract()`가 첫 2개 토큰에는 `+`를 붙이고, 모든 토큰은 단일 공백으로 join하도록 단순화한다.
- `extractPlain()`은 변경하지 않는다.
- 기존 회귀 테스트 기대값을 정규화된 BOOLEAN MODE 문자열로 갱신한다.

검증:

```text
./gradlew.bat test
git diff --check
```

구현 메모:

- `KeywordExtractor.extract()`의 토큰 추출 정책은 유지하고, BOOLEAN MODE 문자열 조립만 `+first +second third fourth` 형태로 단일 공백 join하도록 변경했다.
- 기존 깨진 한글 fixture는 읽을 수 있는 한국어 Unicode ellipsis 샘플로 정리해, 토큰화 한계는 유지하되 테스트 의도를 명확히 했다.

Reviewer 결과:

- `## AI Reviewer 검토 결과` 제목의 Reviewer comment 기준 Blocking 없음.
- Non-blocking: `WORK_PROGRESS.md`의 `검증 예정` 표현을 완료 상태와 맞추는 문서 정정 제안이 있었고, merge 후 후처리에서 `검증`으로 정리했다.
- `check-review-blocking.ps1 -PrNumber 151` 결과 `MERGE_READY`를 확인했다.
- 2026-07-03 기준 PR #151은 merge 완료되었고, Issue #150은 closed 상태다.

---

### #152 - Perspectives KeywordExtractor 일반 토큰 필터링 개선

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/152
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/153
- 상태: merged
- 작업 브랜치: `perf/#152-keyword-generic-token-filtering`
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/domain/detail/util/KeywordExtractor.java`
  - `src/test/java/com/example/globalTimes_be/domain/detail/util/KeywordExtractorTest.java`
  - `docs/backend-improvement/perspectives-matching-sample-snapshot.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- #146 스냅샷에서 드러난 `First`, `round`, `Entre` 같은 일반 토큰이 required token으로 잡히는 문제를 샘플 기반 최소 필터링으로 줄인다.
- #150에서 정규화한 BOOLEAN MODE 포맷 위에서 검색어 후보 품질을 개선한다.
- ranking, entity-aware extraction, translation, DB query, Redis 정책은 변경하지 않는다.

수정 방향:

- `KeywordExtractor`에 기존 stop words와 별도로 샘플 기반 약한 토큰 필터를 둔다.
- `extract()`와 `extractPlain()`이 같은 토큰 후보 추출 정책을 공유하도록 정리한다.
- `BTS`, `Trump`, `Iran`, `Meloni` 같은 핵심 entity 토큰이 유지되는지 테스트로 확인한다.

검증:

```text
./gradlew.bat test
git diff --check
```

구현 메모:

- `KeywordExtractor`의 토큰 후보 추출을 `extractKeywords()`로 모아 `extract()`와 `extractPlain()`이 같은 필터링 정책을 공유하게 했다.
- 샘플 기반 일반 토큰으로 `first`, `round`, `entre`를 분리했다.
- `First round of US-Iran...` 샘플은 `Iran talks...`, `Entre Meloni...` 샘플은 `Meloni Trump...` 중심의 검색어를 생성하도록 테스트 기대값을 갱신했다.
- Reviewer Blocking 반영으로 일반 토큰 필터링 후 후보가 비는 경우 `extract()`와 `extractPlain()` 모두 빈 문자열을 반환하도록 정책을 맞췄다.
- DB-level FULLTEXT 결과 변화는 이번 PR에서 직접 측정하지 않고, `perspectives-matching-sample-snapshot.md`에 후속 측정 후보로 남겼다.

Reviewer 결과:

- 첫 Reviewer comment에서 Blocking 1건이 있었다.
- Blocking: 일반 토큰만 남는 제목에서 `extract()`가 원문 fallback으로 `first`, `round`, `entre`를 다시 살릴 수 있다고 지적했다.
- 반영: 필터링 후 후보가 비면 `extract()`와 `extractPlain()` 모두 빈 문자열을 반환하도록 수정하고 `First round Entre` 회귀 테스트를 추가했다.
- 재검토 결과 `MERGE_READY`, Blocking 없음, Non-blocking 없음으로 확인했다.
- `check-review-blocking.ps1 -PrNumber 153` 결과 `MERGE_READY`를 확인했다.
- 2026-07-04 기준 PR #153은 merge 완료되었고, Issue #152는 closed 상태다.

---

### #154 - Perspectives 데이터 수집 편차와 매칭 품질 해석 한계 LOG

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/154
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/155
- 상태: merged
- 작업 브랜치: `docs/#154-perspectives-source-coverage-log`
- 주요 파일:
  - `docs/backend-improvement/perspectives-source-coverage-limit-log.md`
  - `docs/backend-improvement/perspectives-matching-quality-baseline.md`
  - `docs/backend-improvement/perspectives-matching-sample-snapshot.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- RSS/News API 수집 소스별 갱신 주기, 기사 선택 기준, 국가/언론사별 기사 수 편차가 Perspectives 매칭 품질 해석에 주는 영향을 별도 LOG로 남긴다.
- FULLTEXT, Vector/Embedding, Issue clustering 같은 검색/연관도 기술이 해결할 수 있는 문제와 데이터 공급 문제를 분리한다.
- #152 이후 DB-level FULLTEXT 결과 변화 측정에서 이 LOG를 해석 기준으로 참조하게 한다.

수정 방향:

- `perspectives-source-coverage-limit-log.md`를 추가한다.
- 기존 matching quality baseline과 sample snapshot에서 이 LOG를 참조하도록 링크를 추가한다.
- 코드, DB schema, RSS feed, FULLTEXT query, ranking 정책은 변경하지 않는다.

검증:

```text
git diff --check
```

Reviewer 결과:

- `## AI Reviewer 검토 결과` 제목의 Reviewer comment 기준 Blocking 없음.
- Non-blocking: `WORK_PROGRESS.md`의 `검증 예정` 표현을 완료 상태와 맞추는 문서 정정 제안이 있었고, merge 후 후처리에서 `검증`으로 정리했다.
- `check-review-blocking.ps1 -PrNumber 155` 결과 `MERGE_READY`를 확인했다.
- 2026-07-04 기준 PR #155는 merge 완료되었고, Issue #154는 closed 상태다.

---

### #156 - Perspectives #152 일반 토큰 필터링 FULLTEXT 결과 변화 측정

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/156
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/157
- 상태: merged
- 작업 브랜치: `perf/#156-fulltext-after-generic-token-filter`
- 주요 파일:
  - `docs/backend-improvement/perspectives-fulltext-generic-token-filter-result.md`
  - `docs/backend-improvement/perspectives-matching-sample-snapshot.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- #152에서 일반 토큰 필터링 후 생성 keyword가 바뀐 대표 샘플 8146, 9440의 실제 MySQL FULLTEXT 결과 변화를 측정한다.
- match count, top 50 국가/언어 분포, 상위 반환 예시를 기록한다.
- #154 source coverage LOG를 전제로 검색 품질 문제와 데이터 공급 한계를 구분해 해석한다.

측정 결과:

- 8146은 `+First +round Iran talks` 25건에서 `+Iran +talks ends encouraging` 27건으로 바뀌었고, 상위 결과가 스포츠/라운드 노이즈에서 Iran/US talks 중심으로 이동했다.
- 9440은 `+Entre +Meloni Trump divorce` 0건에서 `+Meloni +Trump divorce italienne` 3건으로 바뀌어 Meloni/Trump 후보가 생겼다.
- 운영 코드, DB schema, FULLTEXT query, ranking, crawler/RSS feed 정책은 변경하지 않았다.

검증:

```text
로컬 MySQL FULLTEXT 측정 쿼리
git diff --check
```

Reviewer 결과:

- `## AI Reviewer 검토 결과` 제목의 Reviewer comment 기준 Blocking 없음.
- Non-blocking: `WORK_PROGRESS.md`의 `검증 예정` 표현을 완료 상태와 맞추는 문서 정정 제안이 있었고, merge 후 후처리에서 `검증`으로 정리했다.
- `check-review-blocking.ps1 -PrNumber 157` 결과 `MERGE_READY`를 확인했다.
- 2026-07-04 기준 PR #157은 merge 완료되었고, Issue #156은 closed 상태다.

---

### #158 - backend-improvement 다음 세션 handoff 문서 정리

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/158
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/159
- 상태: merged
- 작업 브랜치: `docs/#158-next-agent-brief`
- 주요 파일:
  - `docs/backend-improvement/NEXT_AGENT_BRIEF.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- `WORK_PROGRESS.md`가 장기 audit log로 길어지는 것은 유지하되, 새 Codex 세션이 먼저 읽을 짧은 handoff 문서를 추가한다.
- 다음 세션이 workflow, 최근 완료 작업, #110 제외 조건, 다음 추천 후보를 빠르게 파악할 수 있게 한다.
- #156 결과에서 드러난 최신순 정렬의 한계를 바탕으로 다음 추천 후보를 `Perspectives FULLTEXT relevance-first/hybrid ordering 비교`로 명시한다.

수정 방향:

- `NEXT_AGENT_BRIEF.md`를 추가해 읽는 순서, 현재 snapshot, 최근 완료 작업, 다음 추천 이슈, Reviewer 요청 템플릿을 정리한다.
- `BACKLOG.md`의 P3에 #156 이후 다음 후보와 판단 기준을 추가한다.
- 코드, DB schema, API 동작, crawler/RSS feed, Redis 정책은 변경하지 않는다.

검증:

```text
git diff --check
```

Reviewer 결과:

- `## AI Reviewer 검토 결과` 제목의 Reviewer comment 기준 Blocking 없음.
- Non-blocking: `WORK_PROGRESS.md`의 `검증 예정` 표현을 완료 상태와 맞추는 문서 정정 제안이 있었고, merge 후 후처리에서 `검증`으로 정리했다.
- `check-review-blocking.ps1 -PrNumber 159` 결과 `MERGE_READY`를 확인했다.
- 2026-07-07 기준 PR #159는 merge 완료되었고, Issue #158은 closed 상태다.

---

### #160 - Perspectives FULLTEXT relevance-first/hybrid ordering 비교

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/160
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/161
- 상태: merged
- 작업 브랜치: `perf/#160-perspectives-fulltext-ordering-comparison`
- 주요 파일:
  - `docs/backend-improvement/perspectives-fulltext-ordering-comparison.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/NEXT_AGENT_BRIEF.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- #156에서 keyword 품질 개선 후에도 남은 최신순 정렬 노이즈를 분리해 확인한다.
- 현재 운영 쿼리와 같은 latest-first, FULLTEXT score 기반 relevance-first, relevance와 최신성을 함께 보는 hybrid ordering을 같은 샘플에서 비교한다.
- API 동작을 바로 변경하지 않고 DB-level 측정 문서로 후속 코드 변경 판단 근거를 만든다.

조사 메모:

- `ArticleRepository.findPerspectives`는 `MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE)`로 후보를 찾고 `ORDER BY published_at DESC LIMIT 50`으로 정렬한다.
- #156 문서 기준 8146은 keyword 개선 후 Iran/US talks 중심으로 좋아졌지만 Lebanon/ceasefire 같은 인접 노이즈가 최신순 상위에 남았다.
- 2026-07-08 기준 Docker daemon이 실행 중이 아니어서 로컬 MySQL 측정은 Docker Desktop 또는 컨테이너 시작 후 진행해야 한다.
- 이후 Docker 컨테이너 실행을 확인하고 local MySQL에서 측정을 진행했다.

계획:

- 8146, 9440, 8147, 8149 샘플을 우선 대상으로 삼는다.
- 각 샘플에서 latest-first, relevance-first, hybrid ordering의 top results와 score 기반 ordering 차이, 국가/언어 분포를 비교한다.
- source coverage 한계로 인한 누락과 ranking 문제를 분리해 해석한다.

측정 결과:

- 모든 측정 샘플은 match count가 50개 미만이므로 candidate set은 동일하고 top ordering만 달라진다.
- 8146은 latest-first가 최신 Iran/US talks 흐름을 유지하지만 Lebanon/ceasefire 인접 노이즈를 상위에 남겼고, relevance-first는 오래된 March 기사와 broad token overlap을 끌어올렸다.
- 8147은 pure relevance-first가 wrong-context Iran/Trump 기사를 1위로 올렸고, hybrid 후보는 Colombia election 기사를 1위로 유지했다.
- 8149는 good-match control sample로, relevance-first/hybrid가 BTS comeback 관련 강한 score 기사를 더 위로 올렸다.
- 따라서 pure relevance-first로 바로 바꾸기보다는 bounded recency signal을 함께 쓰는 hybrid 후보를 후속 코드 실험 대상으로 보는 것이 안전하다.

검증:

```text
local MySQL FULLTEXT 측정 쿼리
git diff --check
```

Reviewer 결과:

- `## AI Reviewer 검토 결과` 제목의 Reviewer comment 기준 Blocking 없음.
- Non-blocking: `WORK_PROGRESS.md`의 계획 문구가 numeric score 표까지 직접 비교하는 것처럼 보일 수 있어, merge 전 `score 기반 ordering 차이`로 표현을 좁혔다.
- `check-review-blocking.ps1 -PrNumber 161` 결과 `MERGE_READY`를 확인했다.
- 2026-07-07 기준 PR #161은 merge 완료되었고, Issue #160은 closed 상태다.

---

### #162 - 백엔드 개선 후보 overengineering 판단 기준 추가

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/162
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/163
- 상태: merged
- 작업 브랜치: `docs/#162-overengineering-guardrail`
- 주요 파일:
  - `docs/backend-improvement/NEXT_AGENT_BRIEF.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- 새 백엔드 개선 후보를 고를 때마다 “지금 구현하면 과한가?”를 먼저 판단하는 기준을 workflow에 추가한다.
- #160 이후 hybrid ranking query variant를 바로 구현하기보다, 현재 단계에서는 도입 보류와 적용 기준을 docs/ADR로 정리하는 방향을 명시한다.
- 신입 포트폴리오 관점에서 “구현하지 않는 판단”도 근거 있는 결과로 남긴다.

수정 방향:

- `NEXT_AGENT_BRIEF.md`에 Overengineering Guardrail 섹션을 추가한다.
- `BACKLOG.md` 운영 규칙과 P3 다음 판단에 overengineering 기준을 반영한다.
- 코드, API 동작, DB schema, FULLTEXT query, Redis 정책은 변경하지 않는다.

검증:

```text
git diff --check
```

Reviewer 결과:

- `## AI Reviewer 검토 결과` 제목의 Reviewer comment 기준 Blocking 없음.
- Non-blocking: `WORK_PROGRESS.md`의 `검증 예정` 표현을 완료 상태와 맞추는 문서 정정 제안이 있었고, merge 전 `검증`으로 정리했다.
- `check-review-blocking.ps1 -PrNumber 163` 결과 `MERGE_READY`를 확인했다.
- 2026-07-07 기준 PR #163은 merge 완료되었고, Issue #162는 closed 상태다.

---

### #164 - Perspectives ranking policy 도입 보류와 hybrid 후보 적용 기준 정리

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/164
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/165
- 상태: merged
- 작업 브랜치: `docs/#164-perspectives-ranking-policy-adr`
- 주요 파일:
  - `docs/backend-improvement/perspectives-ranking-policy-adr.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/NEXT_AGENT_BRIEF.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- #160 측정 결과를 바탕으로 pure relevance-first를 현재 latest-first의 직접 대체안으로 적용하지 않는 이유를 정리한다.
- hybrid ranking은 폐기하지 않고, 추후 code experiment 후보로 남기되 적용 조건을 명시한다.
- source coverage 한계와 ranking 개선 효과를 분리해서 해석하는 기준을 ADR에 남긴다.
- 신입 포트폴리오 관점에서 "구현하지 않는 판단"도 측정 근거와 함께 설명 가능하게 만든다.

Overengineering 판단:

- 지금 hybrid ranking query variant를 구현하면 운영 API 응답 순서가 바뀌지만, 대표 샘플과 회귀 기준이 아직 부족하다.
- #160 측정은 candidate set이 모두 50건 미만인 조건에서 top ordering 차이를 비교한 것이므로 recall 개선 근거는 아니다.
- pure relevance-first는 wrong-context 기사를 끌어올릴 수 있어 바로 적용하기 위험하다.
- 현재 단계에서는 코드 변경 없이 ADR로 보류 판단과 후속 적용 기준을 남기는 편이 더 적절하다.

수정 방향:

- `perspectives-ranking-policy-adr.md`를 추가해 도입 보류 결정, 근거, 후속 hybrid 실험 조건, non-goals를 정리한다.
- `BACKLOG.md`의 P3 상태를 #164 In Progress로 갱신하고 다음 판단을 ADR 작업 기준으로 좁힌다.
- `NEXT_AGENT_BRIEF.md`가 새 세션에서 #164 진행 상태와 Reviewer 검토 관점을 바로 파악할 수 있게 갱신한다.
- 코드, API 응답, DB schema, FULLTEXT query, Redis 정책은 변경하지 않는다.

검증:

```text
git diff --check
```

Reviewer 결과:

- `## AI Reviewer 검토 결과` 제목의 Reviewer comment 기준 Blocking 없음.
- Non-blocking 없음.
- `check-review-blocking.ps1 -PrNumber 165` 결과 `MERGE_READY`를 확인했다.
- 2026-07-08 기준 PR #165는 merge 완료되었고, Issue #164는 closed 상태다.

---

### #166 - PR 단위 문서 기록과 develop 커밋 이력 정리 기준 추가

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/166
- 상태: PR 기준 진행 기록
- 작업 브랜치: `docs/#166-commit-history-policy`
- 주요 파일:
  - `docs/ai-workflow/reviewer-comment-workflow.md`
  - `docs/ai-workflow/README.md`
  - `docs/backend-improvement/NEXT_AGENT_BRIEF.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`
  - `docs/backend-improvement/BACKLOG.md`

목표:

- merge 후 `WORK_PROGRESS.md`만 갱신하는 후처리 커밋이 반복되어 develop commit history가 잘게 쪼개지는 문제를 줄인다.
- 작업 내용, 검증 결과, docs 기록을 PR 본 작업 커밋에 함께 포함하는 기준을 명확히 한다.
- Reviewer 이후 diff 변경 금지와 최신 diff 기준 재검토 원칙을 유지한다.
- merge 시각이나 closed 상태처럼 사후에만 확정되는 정보는 GitHub PR/Issue 상태로 확인한다.

Overengineering 판단:

- MCP 자동화나 스크립트 구현은 아직 과하다.
- 현재 문제는 문서화된 workflow 기본값이 후처리 커밋을 유도하는 것이므로, 코드 변경 없이 문서 기준을 정리하는 것이 적절하다.
- develop 이력을 깔끔하게 유지하는 기준은 포트폴리오 설명 가능성과 협업 재현성에 직접 도움이 된다.

수정 방향:

- `reviewer-comment-workflow.md`의 merge 후 처리 기준을 GitHub PR/Issue 상태 확인 중심으로 바꾼다.
- `README.md`의 GitHub 기록 기준에서 merge 결과와 다음 상태의 위치를 PR 본문, GitHub PR/Issue 상태, 필요 시 `WORK_PROGRESS.md`로 조정한다.
- `NEXT_AGENT_BRIEF.md`와 이 문서에 #166 진행 상태와 새 기본 원칙을 기록한다.
- 이번 PR 자체도 작업 내용과 docs 기록을 한 커밋에 포함하고, merge 후 별도 기록 커밋을 만들지 않는 기준을 따른다.

검증:

```text
git diff --check
```

---

### #168 - 검색 API FULLTEXT 검색어별 성능 기준선 수립

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/168
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/169
- 상태: merged
- 작업 브랜치: `perf/#168-search-fulltext-term-baseline`
- 주요 파일:
  - `load-tests/k6/search-fulltext-terms.js`
  - `docs/backend-improvement/search-fulltext-term-baseline.md`
  - `docs/backend-improvement/load-test-baseline.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/NEXT_AGENT_BRIEF.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- #133/#134에서 확인한 검색 API FULLTEXT 실행 계획 개선을 API-level p95, 실패율, 결과 수 기준선과 연결한다.
- 영어/한국어/짧은 검색어/결과 적은 검색어를 같은 k6 조건에서 측정할 수 있게 한다.
- Elasticsearch 도입 여부를 바로 결정하지 않고, MySQL FULLTEXT 기반 검색이 어떤 조건에서 안정적인지 설명 가능한 기준을 만든다.
- 이력서에서는 `검색 API MySQL FULLTEXT 실행 계획 분석 및 검색어별 p95/실패율 기준선 수립`으로 압축 가능하게 한다.

Overengineering 판단:

- 지금 Elasticsearch나 query rewrite를 도입하면 과하다.
- 이미 #133/#134에서 쿼리 개선 근거가 있으므로, 다음 단계는 더 큰 기술 도입이 아니라 API 기준선 수치화다.
- 코드/API/DB 동작을 바꾸지 않고 k6 스크립트와 문서 기준선을 추가하는 범위가 현재 단계에 적절하다.

수정 방향:

- 검색어 세트를 순회하는 `search-fulltext-terms.js` k6 스크립트를 추가한다.
- `search_result_count` metric으로 응답의 `data.searchArticles.length`를 기록한다.
- `search-fulltext-term-baseline.md`에 검색어 유형, 실행 방법, 결과 기록 템플릿, 해석 기준, 이력서 문장 후보를 정리한다.
- 기존 `load-test-baseline.md`, `BACKLOG.md`, `NEXT_AGENT_BRIEF.md`에 새 기준선 문서를 연결한다.

검증:

```text
node --check load-tests/k6/search-fulltext-terms.js
k6 inspect load-tests/k6/search-fulltext-terms.js
k6 run load-tests/k6/search-fulltext-terms.js (SEARCH_VUS=1, SEARCH_DURATION=10s, term-by-term)
git diff --check
```

비고:

- 2026-07-09 로컬 Docker MySQL/Redis와 `bootRun` 서버(`localhost:8080`) 기준으로 검색어별 smoke baseline을 기록했다.
- p95/failure/result count: `war` 76.63ms/0.00%/100, `korea` 60.38ms/0.00%/40, `economy` 63.24ms/0.00%/39, `technology` 103.41ms/0.00%/82, `대구` 50.14ms/0.00%/0, `AI` 24.78ms/0.00%/0.
- 이번 PR은 검색어별 기준선 수집을 재현 가능하게 하는 스크립트와 최초 로컬 측정 기록 수립에 집중한다.
- Reviewer 결과 `MERGE_READY`, Blocking 없음으로 확인 후 2026-07-09 기준 PR #169를 squash merge했고 Issue #168은 closed 상태다.

---

### #170 - 기사 원문 크롤링 동기 외부 호출 응답 지연 기준선 수립

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/170
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/171
- 상태: merged
- 작업 브랜치: `perf/#170-article-crawl-latency-baseline`
- 주요 파일:
  - `load-tests/k6/article-crawl-baseline.js`
  - `docs/backend-improvement/article-crawl-latency-baseline.md`
  - `docs/backend-improvement/load-test-baseline.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/NEXT_AGENT_BRIEF.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- #137/#138에서 정리한 기사 원문 크롤링 timeout/fallback 및 외부 I/O 트랜잭션 분리를 API-level p95, 실패율, crawler fallback rate 기준선과 연결한다.
- 기사 요약 API의 동기 외부 크롤링 경로가 사용자 요청 지연에 주는 영향을 cold/warm/fallback 조건으로 설명 가능하게 한다.
- 비동기/Kafka/Redis 캐시 도입 여부를 바로 결정하지 않고, 도입 검토 조건만 남긴다.
- 이력서에서는 `기사 요약 API 동기 외부 크롤링 p95/fallback 기준선 수립`으로 압축 가능하게 한다.

Overengineering 판단:

- 지금 Kafka, async job queue, Redis/local cache를 바로 도입하면 과하다.
- #137/#138에서 timeout/fallback과 트랜잭션 경계 개선은 이미 완료됐지만, 사용자 요청 경로에서 어느 조건이 느린지 수치 기준선이 부족하다.
- 운영 코드, API 응답, DB schema, crawler timeout 값을 바꾸지 않고 k6 스크립트와 문서 기준선을 추가하는 범위가 현재 단계에 적절하다.

수정 방향:

- `GET /api/ai/{id}/summary`를 호출하는 `article-crawl-baseline.js` k6 스크립트를 추가한다.
- `article_crawler_fallback`, `article_summary_success`, `article_summary_payload_size` custom metric으로 summary success/fallback 결과를 분리 기록한다.
- `article-crawl-latency-baseline.md`에 summary hit, crawled-content hit, crawl cold success, crawl fallback 해석 기준과 결과 기록 템플릿을 정리한다.
- #168 merge 상태와 #170 진행 상태를 `BACKLOG.md`, `NEXT_AGENT_BRIEF.md`, `WORK_PROGRESS.md`에 함께 반영한다.

검증:

```text
node --check load-tests/k6/article-crawl-baseline.js
k6 inspect load-tests/k6/article-crawl-baseline.js
k6 run load-tests/k6/article-crawl-baseline.js (ARTICLE_IDS=7366, SCENARIO_LABEL=summary_hit_smoke, ARTICLE_CRAWL_DURATION=10s)
git diff --check
```

비고:

- 일반 summary API는 저장된 `summary`가 있으면 즉시 반환하고, 없으면 `crawledContent` 확인 후 필요 시 외부 크롤링과 Gemini 요약 호출까지 이어진다.
- 따라서 이 기준선은 순수 crawler microbenchmark가 아니라 사용자-facing summary path 기준선이다.
- 2026-07-09 로컬 Docker MySQL/Redis와 `bootRun` 서버 기준 summary hit smoke를 기록했다.
- `ARTICLE_IDS=7366` summary hit 조건에서 p95 332.6ms, failure 0.00%, crawler fallback 0.00%, summary success 100.00%, payload size 420을 확인했다.
- 순수 crawler timing이 필요하면 별도 service-level instrumentation 또는 격리 테스트 이슈로 분리한다.
- Reviewer 결과 `MERGE_READY`, Blocking 없음으로 확인 후 2026-07-09 기준 PR #171을 squash merge했고 Issue #170은 closed 상태다.

---

### #172 - 기사 요약 API 외부 호출 단계별 latency 로그 추가

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/172
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/173
- 상태: merged
- 작업 브랜치: `perf/#172-ai-summary-latency-log`
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/domain/ai/controller/AiController.java`
  - `src/main/java/com/example/globalTimes_be/domain/detail/service/DetailService.java`
  - `docs/backend-improvement/article-crawl-latency-baseline.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/NEXT_AGENT_BRIEF.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- #170/#171에서 만든 user-facing summary path k6 기준선을 내부 단계별 latency 로그와 연결한다.
- summary hit, crawledContent hit, cold crawl, crawler fallback, AI summary, summary save 시간을 민감 정보 없이 구분 가능하게 한다.
- Kafka, async job, Redis/local cache 도입 전 실제 병목 구간을 확인할 근거를 남긴다.
- 이력서에서는 `기사 요약 API 동기 외부 호출 단계별 latency 로깅으로 cache/비동기 처리 판단 기준 수립`으로 압축 가능하게 한다.

Overengineering 판단:

- 지금 비동기 큐, Kafka, Redis/local cache를 바로 추가하면 과하다.
- #170 k6 응답 파싱만으로는 summary hit/crawledContent hit/cold crawl/Gemini 지연을 자동 분리하기 어렵다는 Non-blocking이 있었다.
- API 응답과 저장 정책은 유지하고, 로그에 단계별 latency와 boolean 상태만 남기는 범위가 현재 단계에 적절하다.

수정 방향:

- `AiController#summarizeArticle()`에 `summaryLookupMs`, `crawlContentMs`, `fallbackContentMs`, `aiSummaryMs`, `summarySaveMs`, `totalMs` 로그를 추가한다.
- `DetailService#getArticleCrawledContent()`에 `crawledContentHit`, `crawlAttempted`, `crawlSuccess`, `crawlTargetLookupMs`, `crawlMs`, `saveMs`, `totalMs` 로그를 추가한다.
- 로그에는 기사 원문, 요약 전문, URL, 질문 전문, API key, token, `.env` 값을 남기지 않는다.
- `article-crawl-latency-baseline.md`에 새 로그 필드와 k6 `SCENARIO_LABEL` 연계 해석 기준을 추가한다.

검증:

```text
./gradlew.bat test
git diff --check
```

비고:

- 이번 PR은 운영 코드에 로그를 추가하지만 API 응답 구조, DB schema, crawler timeout, Redis 정책, 비동기 처리 방식은 변경하지 않는다.
- summary hit/crawledContent hit/cold crawl/fallback 구분은 `AiSummary` 로그와 `ArticleCrawlContent` 로그를 함께 보고 판단한다.
- Reviewer 결과 `MERGE_READY`, Blocking/Non-blocking 없음으로 확인 후 2026-07-09 기준 PR #173을 squash merge했고 Issue #172는 closed 상태다.

---

### #174 - 주요 기사 조회 API 고부하 부하 테스트 및 DB 병목 기준선 수립

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/174
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/175
- 상태: merged
- 작업 브랜치: `perf/#174-articles-high-load-db-baseline`
- 주요 파일:
  - `load-tests/k6/articles-read-high-load.js`
  - `docs/backend-improvement/articles-read-high-load-db-baseline.md`
  - `docs/backend-improvement/load-test-baseline.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/NEXT_AGENT_BRIEF.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- 외부 크롤링/Gemini/번역 API가 없는 주요 기사 조회 API를 고부하 조건에서 분리 측정한다.
- `latest`, `cursor`, `popular`, `explore`, `detail`의 p95/오류율과 서버 로그 `dbQueryMs`를 함께 해석할 기준을 만든다.
- k6 고부하 결과만으로 인덱스나 쿼리를 바로 변경하지 않고, 후속 EXPLAIN/인덱스 후보 이슈를 고르는 근거를 남긴다.
- 이력서에서는 `주요 기사 조회 API 고부하 p95/DB 병목 기준선 수립`으로 압축 가능하게 한다.

Overengineering 판단:

- 지금 DB 인덱스 추가나 Repository query 변경을 바로 적용하면 과하다.
- 기존 `api-baseline.js`는 smoke/baseline 성격이 강해 특정 조회 API가 고부하에서 DB 병목을 만드는지 분리하기 어렵다.
- 운영 코드, API 응답, DB schema를 바꾸지 않고 k6 스크립트와 DB 병목 해석 문서를 추가하는 범위가 현재 단계에 적절하다.

수정 방향:

- `articles-read-high-load.js` k6 스크립트를 추가해 latest/cursor/popular/explore/detail API를 scenario와 tag로 분리 측정한다.
- `articles_payload_size` metric으로 응답 크기 변화도 함께 본다.
- `articles-read-high-load-db-baseline.md`에 API별 repository query, 서버 로그, EXPLAIN 후보, 후속 인덱스/쿼리 분석 이슈를 정리한다.
- `load-test-baseline.md`, `BACKLOG.md`, `NEXT_AGENT_BRIEF.md`, `WORK_PROGRESS.md`에 #174 기준선을 연결한다.

검증:

```text
node --check load-tests/k6/articles-read-high-load.js
k6 inspect load-tests/k6/articles-read-high-load.js
k6 run load-tests/k6/articles-read-high-load.js (VU 1 per API, duration 8s, smoke)
SHOW INDEX FROM article
EXPLAIN latest/cursor/popular/explore/detail recent query shapes
git diff --check
```

비고:

- 이번 PR은 DB 병목을 찾기 위한 기준선 수립이며, 실제 DB index/query 변경은 후속 EXPLAIN 이슈로 분리한다.
- 고부하 p95가 높더라도 서버 로그 `dbQueryMs`가 낮으면 DB 병목이 아닐 수 있으므로, k6 결과와 애플리케이션 로그를 함께 해석한다.
- 2026-07-09 로컬 smoke에서 전체 tagged article read p95 96.5ms, failure 0.00%를 확인했다.
- endpoint별 p95는 latest 85.38ms, cursor 59.86ms, popular 88.52ms, explore 109.86ms, detail 95.21ms였다.
- 초기 EXPLAIN에서 popular query shape는 `idx_article_published_at` range 후 `Using filesort`가 확인되어, 고부하 p95/dbQueryMs가 높아질 경우 후속 인덱스 후보로 검토할 수 있다.
- Reviewer 결과 `MERGE_READY`, Blocking 없음으로 확인 후 2026-07-09 기준 PR #175를 squash merge했고 Issue #174는 closed 상태다.

---

### #176 - articles popular 조회 고부하 지표 및 EXPLAIN ANALYZE로 인덱스 개선 필요성 판단

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/176
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/177
- 상태: merged
- 작업 브랜치: `db/#176-popular-filesort-analysis`
- 주요 파일:
  - `docs/backend-improvement/articles-popular-filesort-analysis.md`
  - `docs/backend-improvement/articles-read-high-load-db-baseline.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/NEXT_AGENT_BRIEF.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- #174에서 발견한 `popular` 조회의 `Using filesort`가 현재 데이터 규모에서 실제 병목인지 확인한다.
- 기준선 정리만 반복하지 않고, 기존 k6 스크립트로 바로 고부하 지표를 확인한다.
- MySQL `EXPLAIN ANALYZE`로 content query와 count query의 actual time을 기록한다.
- 인덱스 추가 여부를 수치 기반으로 판단하되, 근거가 약하면 보류한다.
- 이력서에서는 `popular 기사 조회 filesort 병목 검증`으로 압축 가능하게 한다.

Overengineering 판단:

- `Using filesort`가 보인다는 이유만으로 DB 인덱스를 바로 추가하면 과하다.
- 현재 로컬 데이터는 article 9,853건, 최근 30일 조건 2,070건 수준이다.
- #174 smoke에서 `popular` p95가 88.52ms였고, #176의 20 VU 측정에서도 134.87ms였다.
- 50/100 VU 점진 부하에서는 p95가 상승했지만 RPS가 비례해 증가하지 않아 DB index 변경보다 로컬/애플리케이션 처리 한계 확인이 먼저다.
- 따라서 이번 범위는 운영 코드, Repository query, DB schema/index 변경 없이 측정과 판단 기록만 남긴다.

검증:

```text
k6 run load-tests/k6/articles-read-high-load.js (popular 20 VUs, 60s)
k6 run load-tests/k6/articles-read-high-load.js (popular 50 VUs, 30s)
k6 run load-tests/k6/articles-read-high-load.js (popular 100 VUs, 20s)
EXPLAIN popular page 0/page 5 query shape
EXPLAIN ANALYZE popular page 0/page 5 query shape
EXPLAIN ANALYZE popular count query
git diff --check
```

측정 결과:

- k6 조건: `popular` 20 VUs, 60s, `POPULAR_PAGES=0,1,5`, 다른 article read scenario는 1 VU/1s로 최소화
- 전체 tagged article reads: 6,396 requests, p95 135.17ms, failure 0.00%
- `popular`: p95 134.87ms, failure 0.00%
- 50 VUs, 30s: 3,474 requests, RPS 114.33/s, `popular` p95 423.79ms, failure 0.00%
- 100 VUs, 20s: 2,561 requests, RPS 123.61/s, `popular` p95 902.51ms, failure 0.00%
- VUs가 20에서 100으로 늘어도 RPS는 106.29/s에서 123.61/s로만 증가해 로컬 실행환경 또는 애플리케이션 처리 한계가 섞였을 가능성이 있다.
- MySQL page 0 content query: `idx_article_published_at` range scan 2,070 rows 후 `Using filesort`, actual time 약 6.44ms
- MySQL page 5 content query: offset 100 기준 actual time 약 11.5ms
- count query: covering index range scan, actual time 약 0.696ms

판단:

- 현재 데이터 규모와 로컬 고부하 조건에서는 `Using filesort`만으로 즉시 인덱스 추가를 정당화하기 어렵다.
- `popular` p95는 50/100 VU에서 상승했지만, DB-side `EXPLAIN ANALYZE`는 여전히 낮고 RPS가 거의 plateau되어 애플리케이션 로그와 로컬 리소스 지표를 먼저 연결해야 한다.
- `popular` 조회는 watch item으로 남기고, p95와 `[ArticlesPopular] dbQueryMs`가 함께 반복적으로 높아질 때 별도 인덱스 실험 이슈를 연다.
- 이번 실행에서는 `bootRun` stdout 로그 캡처가 안정적으로 되지 않아 `[ArticlesPopular] dbQueryMs`는 기록하지 못했다. 대신 MySQL `EXPLAIN ANALYZE` actual time을 DB-side 근거로 남겼다.
- 다음 후보는 인덱스 구현보다 로컬 성능 측정 시 애플리케이션 latency 로그와 리소스 지표 캡처를 안정화하는 작은 관측성 작업이 더 적절하다.

---

### #178 - 로컬 부하 테스트 latency 로그와 리소스 지표 캡처 안정화

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/178
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/179
- 상태: merged
- 작업 브랜치: `obs/#178-local-load-log-resource-capture`
- 주요 파일:
  - `docs/backend-improvement/local-load-observability-runbook.md`
  - `docs/backend-improvement/load-test-baseline.md`
  - `docs/backend-improvement/articles-popular-filesort-analysis.md`
  - `docs/backend-improvement/articles-read-high-load-db-baseline.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/NEXT_AGENT_BRIEF.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- #176에서 확인한 p95 상승과 DB-side `EXPLAIN ANALYZE` actual time 간 차이를 해석할 수 있도록 같은 실행 구간의 관측 절차를 고정한다.
- k6 p95/RPS/failure, Spring application latency log, Docker MySQL/Redis resource 지표, 로컬 CPU/MEM 상태를 같은 run id로 묶어 기록한다.
- 이후 Gemini/mock 외부 호출 부하 테스트에서 mock latency, Spring 처리 시간, DB/connection/local resource 병목을 구분할 수 있는 최소 관측 기준을 만든다.
- 이력서에서는 `부하 테스트 로그/리소스 관측 기준 수립`으로 압축 가능하게 한다.

Overengineering 판단:

- 현재 단계에서 별도 APM, Prometheus/Grafana, tracing stack을 도입하면 프로젝트 규모와 포트폴리오 설명 난이도 대비 과하다.
- #176의 병목 후보는 DB index 변경보다 관측 데이터 부족이 먼저였으므로, 운영 코드나 스크립트를 바꾸기 전에 문서화된 실행 절차를 고정하는 편이 적절하다.
- 이번 범위는 운영 코드, API 응답, Repository query, DB schema/index, Redis/cache policy, k6 script 변경 없이 runbook과 판단 기준만 남긴다.

작업 내용:

- `local-load-observability-runbook.md`를 추가해 run id, 로그 저장 위치, Spring file logging 실행 방식, k6 결과 캡처 항목, `docker stats --no-stream` 기준, 로컬 리소스 기록 항목을 정리했다.
- high p95가 나타났을 때 `dbQueryMs`, `totalMs`, Docker/local resource 신호 조합별 해석 규칙을 표로 정리했다.
- #176 결과를 runbook 관점에서 재해석해 `popular` p95 상승이 곧바로 DB index 추가 근거가 아니라는 판단을 이어가도록 기록했다.
- 다음 후보로 Gemini 외부 호출 mock latency 부하 테스트를 남기되, 실제 Gemini API 비용을 쓰지 않고 mock 조건별 latency와 failure/timeout을 분리 측정하는 방향을 명시했다.

검증:

```text
git diff --check
```

Reviewer 결과:

- 이번 PR은 문서/runbook-only 변경이며 운영 코드, DB schema/index, Repository query, Redis/cache policy, k6 script 변경이 없다.
- `## AI Reviewer 검토 결과` 제목의 Reviewer comment 기준 `MERGE_READY`, Blocking 없음.
- Non-blocking: `BACKLOG.md` P1 상태 줄에서 진행 중 이슈와 완료 근거를 분리하면 더 읽기 쉽다는 제안이 있었고, merge 전 반영했다.
- Non-blocking: PR 본문과 작업 기록의 "AI Reviewer 생략 가능" 표현은 이번처럼 실제 리뷰를 받은 경우 혼선을 줄 수 있어, 작업 기록은 실제 Reviewer 결과로 정리했다.

---

### #180 - 주요 조회 API 단일 인스턴스 TPS 한계와 병목 지점 정리

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/180
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/181
- 상태: merged
- 작업 브랜치: `perf/#180-single-instance-tps-baseline`
- 주요 파일:
  - `.gitignore`
  - `docs/backend-improvement/single-instance-tps-baseline.md`
  - `docs/backend-improvement/articles-read-high-load-db-baseline.md`
  - `docs/backend-improvement/load-test-baseline.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/NEXT_AGENT_BRIEF.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- 로컬 Docker MySQL/Redis와 단일 `bootRun` Spring Boot 인스턴스 기준으로 `popular` 조회가 어느 정도 RPS까지 안정적인지 측정한다.
- p95, failure rate, RPS, 애플리케이션 `dbQueryMs`/`totalMs`, Docker/local resource 신호를 같은 실행 구간으로 해석한다.
- 멀티 Pod/Kubernetes/클라우드 부하 테스트 없이도 단일 인스턴스 처리량과 포화 구간을 이력서에 설명 가능한 수준으로 남긴다.

Overengineering 판단:

- 지금 Kubernetes, multi Pod, cloud load test, Prometheus/Grafana/APM까지 도입하면 8GB 로컬 환경과 현재 프로젝트 단계에 과하다.
- 이번 이슈는 운영 코드, API 응답, Repository query, DB schema/index, Redis/cache policy, 비동기 처리를 변경하지 않고 측정/판단만 남긴다.
- 100 VU 이상을 무리하게 반복하기보다, 50 VU에서 이미 RPS plateau와 p95 상승이 보여 안전하게 중단했다.

실행 조건:

```text
Run ID: 20260710-1530-single-instance-popular
Server: local bootRun, localhost:8080
DB/cache: Docker MySQL/Redis
k6 script: load-tests/k6/articles-read-high-load.js
Target: popular scenario
Noise scenarios: latest/cursor/explore/detail each 1 VU / 1s
Popular pages: 0,1,5
Sleep: 0.1s
```

측정 결과:

| Popular VUs | Duration | Requests | RPS | Popular avg | Popular p95 | Failure rate |
| ---: | --- | ---: | ---: | ---: | ---: | ---: |
| 5 | 30s | 970 | 24.19/s | 57.61ms | 105.05ms | 0.00% |
| 10 | 30s | 1,841 | 45.91/s | 65.16ms | 94.62ms | 0.00% |
| 20 | 30s | 3,505 | 87.29/s | 71.84ms | 105.48ms | 0.00% |
| 50 | 30s | 3,474 | 86.15/s | 334.86ms | 456.72ms | 0.00% |

비고:

- `Duration`은 `popular` scenario의 active duration이고, `Requests/RPS`는 scenario start offset과 짧은 noise scenario를 포함한 k6 전체 run window 기준 summary 값이다.
- 모든 단계가 같은 script shape로 실행되었으므로 같은 기준의 상대 비교와 포화 판단에는 사용할 수 있지만, 순수 30초 `popular` 단독 TPS로 해석하지 않는다.

판단:

- 20 VU까지는 RPS가 증가하면서 `popular` p95가 약 105ms 수준으로 유지되어 이 로컬 환경의 안정 baseline으로 볼 수 있다.
- 50 VU에서는 RPS가 20 VU 대비 증가하지 않았고 p95가 456.72ms로 상승해 단일 인스턴스 포화 신호로 해석한다.
- failure rate는 모든 단계에서 0.00%였으므로 availability failure가 아니라 latency saturation으로 기록한다.
- 50 VU 말미의 `[ArticlesPopular]` 샘플 로그는 `dbQueryMs` 약 19~51ms, `totalMs` 약 31~74ms 수준이라, k6 p95 상승을 DB index 문제로 바로 해석하기 어렵다.
- 다음 개선은 DB index보다 peak app/thread/CPU 관측 보강 또는 Gemini mock 외부 호출 병목 기준선 쪽이 더 적절하다.

검증:

```text
docker ps
k6 version
Test-NetConnection 127.0.0.1 -Port 8080
k6 run articles-read-high-load.js (popular 5 VUs, 30s)
k6 run articles-read-high-load.js (popular 10 VUs, 30s)
k6 run articles-read-high-load.js (popular 20 VUs, 30s)
k6 run articles-read-high-load.js (popular 50 VUs, 30s)
docker stats --no-stream
```

## 4. 이후 개선 로드맵

### A. #123 이후 바로 할 수 있는 성능 개선

#### 1. cold cache / warm cache 분리 측정

목표:

- Redis 캐시가 비어 있는 첫 요청과 캐시가 채워진 반복 요청의 p95 차이를 분리해 측정한다.

예상 이슈:

```text
[PERF] Perspectives API cold/warm cache 부하 테스트 분리
```

측정 예시:

| 시나리오 | p95 | 오류율 | 의미 |
| --- | --- | --- | --- |
| cold cache |  |  | 번역/FULLTEXT/캐시 저장 포함 |
| warm cache |  |  | Redis hit 중심 |

포트폴리오 문장 후보:

```text
Perspectives API의 cold/warm cache 시나리오를 분리 측정해 Redis 캐시 hit 시 p95 응답 시간이 얼마나 감소하는지 수치화했습니다.
```

#### 2. Perspectives Redis 캐시 개선

목표:

- 반복 요청에서 Redis 캐시 효과를 확인하고 TTL/key 정책을 점검한다.

예상 이슈:

```text
[PERF] Perspectives API Redis 캐시 hit/miss 성능 비교 및 개선
```

검토 후보:

- cache key 정책
- TTL 적정성
- 캐시 역직렬화 실패 시 재계산 정책
- 캐시 저장 실패 시 fallback

#### 3. 검색 API FULLTEXT 성능/안정성 개선

목표:

- 검색어별 응답 시간과 실패율을 측정한다.
- 영어/한국어/다국어 검색어의 동작 차이를 분석한다.

예상 이슈:

```text
[PERF] 검색 API FULLTEXT 성능 및 검색어별 안정성 개선
```

#### 4. 크롤링 안정성 개선

목표:

- AI 요약/질의응답에서 외부 언론사 페이지 크롤링이 사용자 요청에 직접 영향을 주는 문제를 완화한다.

예상 이슈:

```text
[FIX] 기사 원문 크롤링 timeout 및 실패 처리 개선
```

검토 후보:

- Jsoup timeout 설정
- 본문 없음 fallback
- 차단 응답 처리
- 재크롤링 방지
- 트랜잭션 내 외부 I/O 여부 점검

### B. AI Workflow 자동화 후속

#### 1. Reviewer comment 템플릿 강화

현재 `check-review-blocking.ps1`은 `## AI Reviewer 검토 결과` 제목을 기준으로 Reviewer comment를 찾는다.
앞으로는 Reviewer comment 템플릿을 더 엄격히 고정하면 오탐 가능성을 더 줄일 수 있다.

예상 이슈:

```text
[CHORE] AI Reviewer comment template 표준화
```

#### 2. MCP 서버화 후보

지금은 GitHub CLI와 PowerShell 스크립트로 다음을 처리한다.

- PR comment 조회
- 최신 AI Reviewer comment 식별
- Blocking 여부 판정

반복 패턴이 충분히 쌓이면 MCP tool로 확장할 수 있다.

후보 tool:

```text
get_latest_reviewer_comment(prNumber)
extract_blocking_items(prNumber)
get_review_decision(prNumber)
check_issue_scope(prNumber, issueNumber)
append_audit_log(...)
```

단, MCP는 지금 당장 만들기보다 실제 불편함과 반복 작업이 더 쌓인 뒤 진행한다.

### C. 장기 기술 후보와 도입 기준

초기 대화에서 Kafka, RAG, Vector DB, Elasticsearch, MCP 같은 기술 후보를 언급했지만, 현재 원칙은 “기술을 먼저 도입하지 않는다”이다.
포트폴리오에서 중요한 것은 기술 개수가 아니라, 어떤 문제를 측정했고 어떤 근거로 기술을 선택했는지다.

따라서 아래 기술들은 바로 TODO로 구현하지 않고, 문제와 지표가 확인되었을 때 ADR 또는 후속 이슈로 검토한다.

| 기술 후보 | 해결하려는 문제 | 도입 검토 조건 | 우선순위 |
| --- | --- | --- | --- |
| Redis cache 고도화 | 반복 조회, 번역 API 비용, Perspectives 응답 지연 | cold/warm cache p95 차이가 크고 cache hit 시 개선 폭이 확인될 때 | 높음 |
| Elasticsearch | MySQL FULLTEXT 한계, 다국어 검색 품질 부족 | 검색어별 누락/오류/응답 지연이 반복되고 MySQL FULLTEXT 개선으로 부족할 때 | 중간 |
| Vector DB / Embedding | 같은 사건의 표현 차이로 기사 매칭 누락 | 대표 이슈 샘플에서 국가별 관련 기사 매칭 품질 개선 필요가 확인될 때 | 중간 |
| RAG | 기사 요약/질의응답에서 원문 근거 추적과 답변 품질 개선 | AI 응답의 근거 추적, hallucination 방지, 문서/기사 검색 기반 답변이 필요할 때 | 중간 |
| Kafka / Message Queue | 뉴스 수집, 번역, 임베딩, 크롤링 작업의 비동기 처리 | 동기 요청 경로가 외부 API/크롤링 때문에 지연되거나 수집량 증가로 처리량 병목이 확인될 때 | 낮음-중간 |
| Rate Limit | 과다 트래픽에서 외부 API 비용/서버 보호 | k6 부하 테스트에서 특정 API 오류율 증가 또는 외부 API 호출 폭증이 확인될 때 | 중간 |
| MCP 서버 | AI 작업 흐름의 반복 조회/승인/로그 자동화 | PR comment 조회, Blocking 추출, 승인 상태 확인이 반복되어 CLI/스크립트만으로 불편해질 때 | 낮음-중간 |

#### Kafka를 바로 도입하지 않는 이유

Kafka는 대규모 이벤트 스트리밍과 높은 처리량이 필요한 경우 강력하지만, 현재 개인 프로젝트 단계에서는 운영 복잡도가 크다.
뉴스 수집/번역/임베딩/크롤링이 실제로 병목이 되고, 단순 스케줄러나 작업 큐로 부족하다는 근거가 생긴 뒤 검토한다.

후보 시나리오:

```text
RSS/News API 수집 이벤트
→ 중복 제거
→ 번역/정규화
→ 임베딩 생성
→ 검색 인덱스 갱신
```

#### RAG / Vector DB를 바로 도입하지 않는 이유

RAG와 Vector DB는 “같은 사건의 다른 국가 시선 연결”이라는 서비스 핵심과 잘 맞는다.
하지만 먼저 현재 FULLTEXT 기반 매칭의 실패 사례와 대표 이슈 샘플을 정의해야 한다.

후보 시나리오:

```text
대표 글로벌 이슈 샘플 선정
→ 현재 FULLTEXT 매칭 결과 기록
→ 누락 국가/오탐 기사 분석
→ 임베딩 기반 유사도 검색 PoC
→ 비용, 응답 시간, 정확도 비교
```

#### Elasticsearch를 검토할 수 있는 지점

MySQL FULLTEXT는 간단하지만 다국어 검색, 형태소 분석, ranking 제어에 한계가 있다.
검색 API의 실패율과 응답 시간, 검색 품질 문제가 반복적으로 확인되면 Elasticsearch 도입을 ADR로 검토한다.

#### MCP 서버를 검토할 수 있는 지점

현재는 GitHub CLI와 PowerShell 스크립트로 AI Reviewer comment 확인을 처리한다.
MCP는 다음 반복 작업이 많아졌을 때 검토한다.

```text
PR comment 조회
→ 최신 AI Reviewer comment 식별
→ Blocking 추출
→ 승인 상태 확인
→ 위험 변경 파일 감지
→ audit log 기록
```

이 흐름이 2~3개 이상의 실제 개선 PR에서 반복되면, MCP tool로 묶는 것이 자연스럽다.

### #182 - Gemini mock latency 기반 외부 호출 병목 기준선 수립

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/182
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/183
- 작업 브랜치: `perf/#182-gemini-mock-latency-baseline`
- 상태: merged
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/global/config/GeminiConfig.java`
  - `src/main/java/com/example/globalTimes_be/domain/ai/controller/AiController.java`
  - `src/main/resources/application.yml`
  - `load-tests/mock-gemini-server.js`
  - `docs/backend-improvement/gemini-mock-latency-baseline.md`

목표:

- 실제 Gemini API 비용, quota, rate limit, 응답 편차 없이 외부 AI 호출 지연을 통제한다.
- `GET /api/ai/{id}/summary` 사용자-facing 경로에서 mock AI latency가 p95, RPS/TPS, failure rate에 주는 영향을 측정할 수 있게 한다.
- 기존 k6 VU/RPS 결과를 바탕으로 8GB 로컬 노트북에서 안전한 점진 부하 범위를 정한다.

Overengineering 판단:

- Kafka, async queue, Redis cache, real Gemini 부하 테스트는 현재 증거 대비 과하다.
- summary cache 때문에 첫 요청 이후 Gemini 경로가 skip되므로, 테스트 전용 `AI_SUMMARY_SAVE_ENABLED=false`가 필요하다.
- production 기본값은 `true`로 유지해 기존 API 동작을 바꾸지 않는다.
- `gemini.base-url`도 기본값을 실제 Gemini URL로 유지하고, 로컬 테스트에서만 mock server URL로 override한다.

조사/근거:

- #180 단일 인스턴스 popular 기준선은 20 VU에서 약 87 RPS, p95 약 105ms, failure 0.00%였다.
- #180 50 VU에서는 RPS가 약 86으로 증가하지 않고 p95가 456ms로 상승해 로컬/애플리케이션 포화 신호로 해석했다.
- #176 popular-focused 20/50/100 VU도 RPS가 `106.29/s -> 114.33/s -> 123.61/s`로 완만히 증가하는 동안 p95는 `134.87ms -> 423.79ms -> 902.51ms`로 크게 상승했다.
- 따라서 Gemini mock 테스트는 50+ VU가 아니라 `1 -> 3 -> 5 -> 10 -> 20 VU` 순서로 점진 수행한다.

구현 및 측정 결과:

- `gemini.base-url`을 설정화해 local mock server로 redirect할 수 있게 한다.
- `ai.summary-save-enabled`를 추가해 로컬 부하 테스트에서만 summary 저장을 끄고 반복 AI-path 측정을 가능하게 한다.
- Node 내장 `http` 기반 mock Gemini server를 추가한다.
- `200ms`는 1/3/5/10/20 VU에서 p95 `262.90~289.96ms`, 최대 57.80 RPS, failure 0.00%였다.
- `1000ms`는 1/3/5 VU에서 p95 `1040.88~1057.59ms`, 최대 4.39 RPS, failure 0.00%였다.
- `3000ms`는 1/3/5 VU에서 p95 `3039.06~3051.71ms`, 최대 1.59 RPS, failure 0.00%였다.
- mock 500 응답은 1 VU/15초에서 43건 모두 backend 5xx로 전파되어 failure 100.00%였고, 의도대로 k6 threshold를 위반했다.
- 대표 로그는 `summaryHit=false`, `aiRequested=true`, `summarySaveMs=0`을 확인했으며 `aiSummaryMs`는 200ms/1s/3s 조건에서 각각 224ms/1033ms/3020ms였다.
- 테스트에 사용한 기사 summary는 측정 전 백업하고 측정 후 원래 값으로 복원했으며 임시 DB table도 제거했다.
- 결과는 `docs/backend-improvement/gemini-mock-latency-baseline.md`에 실행 조건과 해석 경계까지 기록했다.

판단:

- 정상 경로의 p95는 mock latency를 거의 그대로 따라 외부 동기 호출이 사용자 응답 지연을 지배한다.
- 테스트 범위에서는 VU 증가에 따른 처리량 증가가 유지되어 로컬 포화 신호는 발견되지 않았다.
- 실제 Gemini quota/network/rate limit을 제외한 mock 결과이므로 운영 용량으로 일반화하지 않는다.
- Gemini 5xx가 사용자-facing 5xx로 전파되는 현상은 확인했지만, timeout/fallback/async 도입은 별도 이슈에서 이 기준선과 비교해 판단한다.

Reviewer 필요성:

- 운영 기본 동작은 유지하지만 runtime config와 테스트 스크립트가 추가되므로 Reviewer 검토 대상이다.
- 최초 Reviewer는 실제 측정 없이 #182를 닫는 상태 불일치를 Blocking으로 지적했고, 위 측정과 `Verified` 상태 갱신으로 반영했다.

### #184 - Gemini 요약 API timeout 상한 및 upstream 오류 분리

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/184
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/185
- 작업 브랜치: `fix/#184-gemini-timeout-upstream-errors`
- 상태: merged
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/domain/ai/service/AiService.java`
  - `src/main/java/com/example/globalTimes_be/domain/detail/exception/DetailErrorStatus.java`
  - `src/main/resources/application.yml`
  - `src/test/java/com/example/globalTimes_be/domain/ai/service/AiServiceTest.java`
  - `docs/backend-improvement/gemini-timeout-upstream-error-policy.md`

문제:

- #182에서 Gemini mock 500이 43/43건 backend 500으로 전파되어 내부 오류와 외부 의존성 장애를 구분할 수 없었다.
- `AiService`는 고정 90초 timeout을 사용해 장시간 upstream 지연이 사용자 요청을 오래 붙잡을 수 있었다.
- 개선 전 mock 15초/1 VU/35초 조건은 3건 성공, 평균 15.43초, p95 16.08초였다.

Overengineering 판단:

- 현재 200ms/20 VU 범위에서 로컬 포화 신호가 없어 async queue, Kafka, circuit breaker를 바로 도입할 근거가 부족하다.
- 기사 원문을 summary처럼 반환하는 fallback은 API 의미를 바꾸므로 제외한다.
- 이번 이슈는 timeout 상한과 오류 원인 분리만 적용하고 SSE/Trend Gemini 경로는 변경하지 않는다.

구현:

- `gemini.timeout-ms` 기본값을 10000ms로 추가하고 기존 고정 90초를 교체했다.
- Gemini non-2xx는 502 Bad Gateway, timeout은 504 Gateway Timeout, 내부 파싱 오류는 기존 500으로 분리했다.
- 외부 오류 body 대신 status만 로그에 남기고 timeout 로그에는 설정값만 기록한다.
- JDK local HTTP server 기반 `AiServiceTest`로 정상, 502, 504, 내부 500을 회귀 테스트한다.

검증:

- 전체 Gradle test 22건 통과.
- 개선 후 동일 mock 15초 조건은 4건 504, 평균 10.16초, p95 10.42초로 대기 상한이 줄었다.
- 단건 API 검증은 mock 15초에서 504/10.04초, mock 500/200ms에서 502/0.31초, mock 3초 정상 조건에서 200/3.09초였다.
- 개선 후 long-delay k6 threshold 실패는 의도된 결과이며, 성공률 개선이 아니라 bounded wait와 오류 분리가 목표다.
- 테스트 기사 summary는 원복했고 임시 DB table은 제거했다.

판단:

- 이력서에서는 `Gemini 외부 호출 timeout 및 502/504 오류 분리, mock before/after 검증`으로 압축할 수 있다.
- 실제 Gemini latency나 처리량으로 일반화하지 않는다.
- 후속 async/retry/circuit breaker는 별도 근거와 이슈 승인 전까지 구현하지 않는다.

Reviewer 필요성:

- 운영 timeout 기본값과 HTTP 오류 응답이 변경되므로 Reviewer 검토가 필요하다.

### #186 - Gemini 동기 호출 servlet thread 포화 및 API 영향 측정

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/186
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/187
- 작업 브랜치: `perf/#186-gemini-thread-saturation`
- 상태: Merged
- 주요 파일:
  - `build.gradle`
  - `src/main/resources/application.yml`
  - `load-tests/k6/gemini-thread-saturation.js`
  - `docs/backend-improvement/gemini-servlet-thread-saturation-baseline.md`

목표:

- Gemini 3초 동기 호출 중 servlet request thread가 점유되는 구조를 Tomcat thread metric과 k6 지표로 수치화한다.
- summary 부하가 증가할 때 Tomcat request thread ceiling 도달과 동시 `popular` 조회 p95의 지연 전파를 같은 실행 구간에서 확인한다.

Overengineering 판단:

- async, queue, retry, circuit breaker를 바로 구현하지 않고 현재 동기 구조의 실제 포화 근거를 먼저 측정한다.
- 로컬 Tomcat 최대 thread를 20개로 제한해 8GB 노트북에서 낮은 VU로 재현 가능한 실험을 구성한다.
- Actuator web endpoint는 기본 노출하지 않고 로컬 부하 테스트 환경변수에서만 health/metrics endpoint를 노출한다.
- Tomcat 세부 thread metric에 필요한 MBean registry도 로컬 실행 환경변수에서만 활성화한다.

구현 및 검증 계획:

- summary VU를 `5 -> 10 -> 20 -> 50`, `popular`는 5 VU로 고정한다.
- k6에서 summary/popular 요청 수, p95, failure를 분리하고 `tomcat.threads.busy/current/config.max`를 함께 샘플링한다.
- mock 3초 조건에서 summary 저장을 비활성화해 모든 요청이 외부 호출 경로를 타게 한다.
- 명확한 포화가 나타나면 노트북 안전을 위해 다음 VU 단계를 생략할 수 있다.
- 측정 후 async 적용 또는 보류 판단을 별도 후속 이슈 기준으로 남긴다.

구현 및 검증 결과:

- Spring Boot Actuator를 추가하고 기본 web exposure는 비워 두며, 로컬 환경변수에서만 health/metrics와 Tomcat MBean registry를 활성화한다.
- `gemini-thread-saturation.js`에서 summary/popular 요청 수, RPS, p95, failure와 Tomcat busy/current/max thread를 같은 실행 구간에 기록한다.
- popular-only 5 VU 대조군은 758건, 24.55 RPS, p95 154.46ms, busy peak 6/20이었다.
- summary 5 VU는 50건/1.55 RPS/p95 3.30초, popular p95 199.10ms, busy peak 11/20이었다.
- summary 10 VU는 100건/3.09 RPS/p95 3.20초, popular p95 170.39ms, busy peak 16/20이었다.
- summary 20 VU는 200건/6.09 RPS/p95 3.23초, busy/current 20/20으로 포화됐다.
- summary RPS는 `1.55 -> 3.09 -> 6.09`로 계속 증가했으므로 20 VU 이후 처리량 plateau 자체를 관측했다고 해석하지 않는다.
- 같은 20 VU 실행에서 popular는 122건/3.71 RPS/p95 2.86초로, 대조군보다 p95가 약 18.5배 증가하고 RPS가 약 84.9% 감소했다. 실패율은 두 조건 모두 0.00%였다.
- 20 VU 말미 popular 로그는 `dbQueryMs=36~42`, `totalMs=68~76`이어서 client p95 2.86초의 대부분은 DB 쿼리보다 servlet thread 대기로 해석한다.
- MySQL/Redis의 측정 직후 point-in-time CPU는 약 1.09%/0.75%, 메모리는 약 472.7MiB/11.51MiB였고 Spring Boot working set은 약 510MiB였다.
- 20 VU에서 포화와 다른 API 지연 전파가 이미 명확해 50 VU는 안전 중단 기준에 따라 생략했다.
- 테스트 기사 7366 summary는 측정 전 백업하고 기존 길이 420으로 복원했으며 임시 백업 테이블과 mock/backend 프로세스를 정리했다.

판단:

- async가 Gemini 자체의 3초 응답을 줄이지는 않지만, servlet thread 점유와 다른 API queueing을 줄일 수 있는 비교 근거가 생겼다.
- 후속 이슈는 동일한 20-thread/3-second mock/20 summary VU/5 popular VU 조건에서 최소 async 경계의 before/after만 검증한다.
- 실제 Gemini 처리량, 운영 SLA, 멀티 인스턴스 capacity로 일반화하지 않는다.

Reviewer 필요성:

- runtime dependency, metrics 노출 설정, k6 스크립트가 변경되므로 Reviewer 검토가 필요하다.

### #188 - Gemini summary Servlet async 전환 전후 thread 점유 비교

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/188
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/189
- 작업 브랜치: `perf/#188-gemini-servlet-async-comparison`
- 상태: Merged
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/global/config/AiSummaryAsyncConfig.java`
  - `src/main/java/com/example/globalTimes_be/domain/ai/controller/AiController.java`
  - `src/main/java/com/example/globalTimes_be/global/exception/GlobalErrorHandler.java`
  - `load-tests/k6/gemini-thread-saturation.js`
  - `docs/backend-improvement/gemini-servlet-async-comparison.md`

목표:

- #186의 20/20 Tomcat thread ceiling과 popular queueing 근거를 실제 async before/after 결과로 연결한다.
- async가 Gemini 자체 latency를 줄이는지보다 unrelated API의 request-thread availability를 보호하는지 검증한다.

Overengineering 판단:

- 전면 WebFlux나 Kafka보다 기존 MVC/JPA 흐름을 유지하는 `WebAsyncTask + bounded executor`를 최소 변경으로 선택한다.
- blocking work는 제거되지 않고 전용 worker로 이동하므로 executor active/queued metric을 함께 기록한다.
- 50 VU는 20-thread ceiling 이후 처리량 shape와 async 격리 효과를 비교하는 근거로만 사용한다.

구현:

- summary API는 기존 orchestration을 `aiSummaryExecutor`에서 실행하는 `WebAsyncTask`를 반환한다.
- executor 기본값은 core/max 20, queue 100이며 모두 환경변수로 조정 가능하다.
- MVC async timeout 기본값은 15초다.
- executor rejection과 timeout interruption은 503, Gemini non-2xx는 502, Gemini timeout은 504, 내부 오류는 500을 유지한다.
- k6는 `executor.active`, `executor.queued`, `executor.pool.size`를 Tomcat metric과 함께 수집한다.

검증:

- 동기 50 VU는 summary 220건/6.28 RPS/p95 8.98초, popular 32건/0.91 RPS/p95 5.97초, Tomcat busy 20/20이었다.
- async 20 VU는 summary 6.12 RPS/p95 3.39초, popular 23.38 RPS/p95 160.99ms, Tomcat busy peak 6, executor active 20/queued 0이었다.
- async 50 VU는 summary 220건/6.29 RPS/p95 9.15초, popular 784건/22.40 RPS/p95 172.98ms, Tomcat busy peak 8이었다.
- async 50 VU executor는 active 20/queued 30으로, 병목이 제거되지 않고 bounded pool로 격리됐음을 확인했다.
- mock 500은 async dispatch에서도 502를 유지했고, local 1초 MVC async timeout은 약 1.81초에 503을 반환했다.
- 전체 Gradle test에 controller defer, task rejection 503, interruption 503, unexpected 500 회귀 테스트를 포함한다.
- 테스트 기사 summary는 기존 길이 420으로 복원했고 임시 table 및 backend/mock 프로세스를 제거했다.

판단:

- 50 VU에서 popular p95가 약 97.1% 감소하고 RPS가 약 24.5배로 회복해 Servlet request-thread 격리 효과가 확인됐다.
- summary 자체 capacity/latency는 개선되지 않았으므로 외부 API 성능 개선 또는 완전한 non-blocking 처리로 표현하지 않는다.
- queue peak 30과 503 overload 정책을 남겼으며 Kafka/WebFlux 도입은 별도 요구가 생기기 전까지 보류한다.

Reviewer 필요성:

- API 실행 방식, executor/timeout 설정, 503 오류 정책, k6 계측이 변경되므로 Reviewer 검토가 필요하다.

### #190 - Gemini async executor 포화 503 및 queue 보호 검증

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/190
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/191
- 작업 브랜치: `test/#190-gemini-executor-overload`
- 상태: Merged
- 주요 파일:
  - `load-tests/k6/gemini-thread-saturation.js`
  - `docs/backend-improvement/gemini-executor-overload-protection.md`

목표:

- #188에서 실제로 도달하지 않은 bounded executor queue 포화와 HTTP 503 rejection 경로를 낮은 로컬 자원으로 재현한다.
- 포화 중 popular API 보호와 부하 종료 후 executor 회복을 함께 확인한다.

Overengineering 판단:

- 기본 worker/queue를 바로 조정하거나 신규 미들웨어를 도입하지 않고, 로컬 환경변수로 5 workers/queue 10을 구성해 기존 보호 동작을 먼저 측정한다.
- 결과는 EC2 또는 대규모 운영 capacity로 일반화하지 않고 향후 인스턴스별 설정과 부하 테스트의 기반으로 한정한다.

계획:

- k6에서 summary HTTP 200/503과 rejection 비율을 분리한다.
- mock Gemini 3초, summary `10 -> 15 -> 20 -> 30 VU`, popular 5 VU, metrics 1 VU를 점진 실행한다.
- executor active/queued, Tomcat busy, popular p95와 포화 후 낮은 VU 회복 결과를 기록한다.
- 기본 executor 20/100 설정 변경은 이번 측정 결과와 별도 승인 없이는 진행하지 않는다.

구현 및 검증 결과:

- k6가 summary HTTP 200/503/unexpected 응답 수와 accepted/rejected latency를 분리하도록 보강했다.
- 로컬 executor 5 workers/queue 10, Gemini mock 3초, popular 5 VU, metrics 1 VU 조건으로 10/15/20/30 VU를 각각 30초 실행했다.
- 10 VU는 51/51건 200, queue peak 5, accepted p95 6.75초, popular p95 371.10ms였다.
- 15 VU는 55/55건 200, active 5/queued 10, accepted p95 9.35초, popular p95 163.49ms였다.
- 20 VU는 200 55건/503 1,158건, accepted p95 9.41초/rejected p95 49.55ms, queue peak 10, popular p95 254.51ms였다.
- 30 VU는 200 55건/503 3,720건, accepted p95 9.34초/rejected p95 34.25ms, queue peak 10, popular p95 256.03ms였다.
- 모든 단계에서 unexpected summary status, popular failure, metrics failure는 0이었다.
- 20/30 VU의 높은 503 비율은 fast rejection 뒤 0.1초마다 재요청하는 closed-model 특성이므로 운영 실패율이나 capacity로 일반화하지 않는다.
- 30 VU 종료 직후 active/queued는 0/0이었고 recovery 5 VU에서 25/25건 200, queue peak 0, summary p95 3.68초를 확인했다.
- 앱 working set은 20 VU 종료 후 약 482MiB였고 MySQL/Redis는 약 486MiB/11.5MiB로 안정적이었다.
- 테스트 기사 7366 summary를 기존 길이 420으로 복원하고 임시 backup table 및 backend/mock 프로세스를 제거했다.

판단:

- queue 10 상한과 실제 HTTP 503 dispatch, 포화 후 회복이 검증됐다.
- queue 증가는 처리량을 늘리지 않으며, 15 VU accepted p95 9.35초는 5-worker 기준 queue 두 batch 대기를 보여준다.
- 기본 20/100은 이번 축소 실험만으로 변경하지 않는다. 고정 인스턴스 환경에서 arrival RPS, Gemini latency/quota, 허용 대기시간을 함께 측정한 뒤 조정한다.
- 빠른 503에 즉시 재시도하면 rejection storm을 만들 수 있으므로 실제 client는 delay/backoff가 필요하다.

Reviewer 필요성:

- k6 시나리오와 측정 문서가 변경되므로 Reviewer 검토가 필요하다.
- 최초 Reviewer는 unexpected status를 5%까지 허용하는 threshold와 빠른 503이 섞인 aggregate summary p95 threshold를 Blocking으로 지적했다.
- unexpected threshold를 `rate==0`, latency threshold를 HTTP 200 전용 `summary_accepted_duration p95<15초`로 변경해 두 Blocking을 반영했다.

---

## 5. 다음 세션에서 바로 이어가기 위한 시작 프롬프트

새 Codex 세션에서 이어갈 때는 먼저 `docs/backend-improvement/NEXT_AGENT_BRIEF.md`를 읽고, 더 자세한 이력이 필요할 때 이 문서를 읽는다.
아래 내용은 간단한 시작 프롬프트로 사용할 수 있다.

```text
GlobalTimes_BeSide 백엔드 개선 작업을 이어서 진행하려고 합니다.

먼저 docs/backend-improvement/WORK_PROGRESS.md 를 읽고 현재까지의 작업 흐름을 파악해줘.
우리는 Issue → Branch → 조사 → 계획 → 승인 → 구현 → 테스트 → PR → AI Reviewer comment → Blocking 확인 → merge 순서로 작업합니다.

현재까지 완료한 큰 흐름은 다음과 같습니다.
- #123/#126: 검색 API LazyInitializationException으로 인한 500 응답 수정
- #127/#128: Perspectives API cold/warm Redis cache 부하 테스트 분리
- #129/#130: Perspectives Redis cache 삭제/무효화/stale 허용 정책 문서화
- #131/#132: Perspectives FULLTEXT 쿼리 EXPLAIN 분석
- #133/#134: 검색 API FULLTEXT 성능 및 검색어별 안정성 분석, 중복 OR MATCH 최적화
- #137/#138: 기사 원문 크롤링 timeout/fallback 및 외부 I/O 트랜잭션 분리

바로 구현하지 말고,
1. 현재 develop 최신화
2. `WORK_PROGRESS.md`의 In Progress 작업과 최근 merged PR을 확인
3. 진행 중인 작업이 있으면 해당 Issue/PR/브랜치 상태를 확인
4. 진행 중인 작업이 없으면 이후 개선 로드맵에서 다음 후보를 제안
5. 관련 코드와 문서를 조사
6. 원인/범위/수정 계획을 제안
7. 사용자 승인 후 구현
순서로 진행해주세요.

다음 후보는 `WORK_PROGRESS.md`의 이후 개선 로드맵과 열린 GitHub Issue를 먼저 확인한 뒤 제안해줘.
현재 장기 열린 이슈로 #110 `[Troubleshooting] 서비스 설계의 근본적 한계`가 남아 있으므로, 이것이 바로 구현 이슈인지 아니면 별도 정리/문서화 이슈인지 먼저 판단해주세요.
```

## 6. 이 문서를 GitHub에 올릴지에 대한 판단

이 문서는 GitHub에 올리는 것을 추천한다.

이유:

- 작업 흐름과 의사결정이 PR/Issue 링크와 함께 남는다.
- context가 사라져도 새 세션에서 이어가기 쉽다.
- 포트폴리오 롱 자료를 작성할 때 근거 자료가 된다.
- AI를 단순 사용한 것이 아니라, 계획/승인/리뷰/검증 흐름으로 운영했다는 증거가 된다.

단, 아래 내용은 GitHub에 올리지 않는다.

- API 키, 토큰, 비밀번호
- `.env` 내용
- 원본 프롬프트 전문 중 민감 정보
- 외부 API 응답 전문
- 개인 정보 또는 사용자 데이터 원문

현재 문서는 민감 정보를 포함하지 않고, Issue/PR 링크와 작업 판단 중심이므로 GitHub에 올려도 된다.
