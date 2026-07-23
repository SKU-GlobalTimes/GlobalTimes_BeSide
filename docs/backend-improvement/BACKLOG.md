# 백엔드 개선 Backlog

## 목적

GlobalTimes 백엔드의 개선 작업을 문제 정의부터 검증 결과까지 추적 가능하게 관리한다.
각 항목은 GitHub Issue, 작업 브랜치, PR, 테스트 및 측정 결과로 연결한다.

## Current Active Work

- 없음

## Recently Completed

### P1. Perspectives 실제 DB 정답 표본 Precision@5 기준선

- 상태: `Done` ([#227](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/227), [PR #228](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/228))
- AS-IS: 실제 DB sample은 정성 분류만 있고 기대 관련 기사 ID와 Precision@5·Hit@5가 없어 품질과 실패 원인을 반복 비교하기 어려웠다.
- TO-BE: 선행 측정에서 이어받은 대표 기사 6건과 FULLTEXT 상위 5개 결과를 고정 라벨로 판정했다. candidate 0건은 coverage 또는 retrieval 원인 미확정으로 남겼다.
- 측정 결과: strict `Precision@5=0.267`, `Useful Precision@5=0.467`, `Hit@5=0.500`을 확인했다. 전체 9,814건의 정확도로 해석하지 않는다.
- 번역 경계: 일일 general-model quota를 1,000자로 제한하고 비영어 표본 2건·총 52자만 실제 번역했으며 추가 호출은 하지 않는다.
- 검증: read-only SQL 전체 MySQL 8 실행, Backend CI 통과, AI Reviewer Blocking 없음·MERGE_READY.
- 근거: `docs/backend-improvement/perspectives-labeled-quality-baseline.md`, `docs/backend-improvement/sql/perspectives-labeled-quality-sample.sql`

### P1. Perspectives 다국어 번역·FULLTEXT 경로 원인 분리 회귀 검증

- 상태: `Done` ([#225](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/225), [PR #226](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/226))
- AS-IS: 번역 실패 fallback은 mock 단위 테스트만 있고 비영어 기사에서 번역·원문 키워드가 실제 MySQL FULLTEXT를 거쳐 병합되는 경로는 자동 검증되지 않았다.
- TO-BE: 통제된 fixture에서 관련 데이터 존재 여부와 mock 번역 결과를 분리해 coverage 부재·matching 실패·fallback·중복 제거 조건을 실제 MySQL로 검증했다.
- 범위: 테스트와 검증 문서로 제한했고 운영 코드·schema·API·ranking·번역 저장, 실제 외부 API·compose DB, 신규 검색 기술은 제외했다.
- 검증: MySQL FULLTEXT 기반 5개 통제 시나리오와 전체 76개 테스트 및 Backend CI가 통과했고 AI Reviewer는 Blocking 없음·MERGE_READY로 판정했다. 실제 번역 품질·source coverage·semantic similarity 개선은 주장하지 않는다.
- 근거: `docs/backend-improvement/perspectives-multilingual-fulltext-integration.md`

### P1. RSS·News API source별 freshness·coverage 기준선 및 수집 통계 보강

- 상태: `Done` ([#223](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/223), [PR #224](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/224))
- AS-IS: 백엔드 scheduler 주기는 알 수 있지만 source별 실제 갱신 시점·신규 기사 수·언어 및 국가 coverage가 측정되지 않아 매칭 결과 부족의 원인을 분리하기 어려웠다.
- TO-BE: 기존 DB snapshot SQL과 수집 batch 로그에 source·country·language·category 분포, 최초·최신 발행 시각, freshness를 기록했다.
- 범위: 기존 컬럼·로그·mock fixture·문서로 제한했다. 실제 외부 호출, 신규 schema·관측 stack, 기사 번역, ranking, retry·Kafka는 제외했다.
- 검증: mock batch 통계 fixture와 전체 71개 테스트 및 Backend CI가 통과했고 AI Reviewer는 Blocking 없음·MERGE_READY로 판정했다. 실행 중인 compose DB가 없어 실데이터 snapshot 수치는 주장하지 않았다.
- 근거: `docs/backend-improvement/collection-freshness-coverage-baseline.md`, `docs/backend-improvement/sql/collection-coverage-snapshot.sql`

### P1. 익명 채팅 Redis 동시 요청 데이터 유실 방지 및 회귀 검증

- 상태: `Done` ([#221](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/221), [PR #222](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/222))
- AS-IS: 세션·기사별 대화와 세션별 최근 기사 인덱스를 String JSON으로 조회·수정·재저장해 동시 갱신이 서로를 덮어쓸 수 있었다.
- TO-BE: Redis List·Sorted Set의 개별 추가 명령으로 전체 JSON 덮어쓰기를 제거하고 `ZADD GT`로 지연된 과거 최근 활동 score의 역전 갱신을 차단했다.
- 범위: 익명 채팅 Redis 저장 경로와 Redis Testcontainers 회귀 테스트로 제한했다. Lua, MULTI/EXEC, 분산 락, Kafka, Gemini 호출, 로그인 MySQL 채팅은 제외했다.
- 검증: 동시 20건에서 기존 JSON 1건·List 20건 보존, 서로 다른 기사 인덱스 20/20건 보존을 각각 3회 확인했다. score 200 이후 도착한 score 100을 거부했고 Redis 집중 테스트 9개·전체 67개 테스트·Backend CI가 통과했다.

### P1. 백엔드 개선 정량 결과 인덱스 정리

- 상태: `Done` ([#218](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/218), [PR #220](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/220))
- AS-IS: k6·Mock·Testcontainers 기반 성과와 한계가 여러 PR·문서에 분산돼 문제·조치·결과의 연결을 추적하기 어려웠다.
- TO-BE: 문제·개선·정량 결과·검증 도구·원본 근거를 도메인별로 연결하고 기준선과 실제 개선을 구분했다.
- 범위: 기존 근거의 문서 인덱싱으로 제한했으며 신규 측정, 코드·스크립트 변경, 신규 기술 도입은 제외했다.
- 검증: 원본 수치와 PR 근거, 상대 Markdown 링크 17개를 교차 확인했고 Backend CI가 통과했다.

### P1. 스크랩 목록 N+1 및 ID별 반복 조회 제거

- 상태: `Done` ([#216](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/216), [PR #217](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/217))
- AS-IS: 로그인 목록은 Scrap·Article·Source LAZY 조회가 연쇄되고, 비로그인 호환 목록은 요청 ID마다 article을 개별 조회했다.
- TO-BE: 동일한 MySQL 100건 fixture에서 projection 일괄 조회로 SQL 증가 구조를 제거하고 기존 응답 의미를 유지한다.
- 범위: 두 스크랩 조회 query와 Testcontainers 회귀 검증으로 제한했다. pagination, Redis, 신규 schema/index, toggle 동시성은 제외했다.
- 검증: 로그인 SQL `201 → 1`·entity `300 → 0`, 비로그인 SQL `200 → 1`·entity `200 → 0`; DTO 전체 필드와 최신순·요청순·중복·누락·nullable Source 회귀 및 전체 58개 테스트·Backend CI 통과.

### P1. 채팅 목록 전체 이력 로딩 및 N+1 제거

- 상태: `Done` ([#214](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/214), [PR #215](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/215))
- AS-IS: 사용자 전체 채팅을 entity로 적재해 Java에서 기사별 최신 행을 고르고, LAZY article 접근으로 기사 수만큼 추가 SQL을 실행했다.
- TO-BE: MySQL 8 window 함수와 projection으로 사용자·기사별 최신 대화 1건만 단일 SQL로 조회하고 기존 DTO·정렬 의미를 유지한다.
- 범위: 채팅 팝업 목록 query와 MySQL Testcontainers 회귀 검증으로 제한했다. pagination, 신규 인덱스/Flyway, Redis, 익명 채팅은 제외했다.
- 검증: 5,000개 합성 채팅·100개 기사에서 SQL `101 → 1`, entity load `5,100 → 0`, 두 로컬 실행의 service elapsed `83.0~86.8%` 감소; 전체 55개 테스트와 Backend CI 통과.

### P1. News API 수집 부분 실패 격리 및 재실행 E2E 검증

- 상태: `Done` ([#212](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/212), [PR #213](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/213))
- AS-IS: DTO와 mock repository 단위 테스트만 있어 외부 HTTP 일부 실패부터 MySQL 저장까지의 수집 경로가 자동 검증되지 않는다.
- TO-BE: random-port mock upstream과 MySQL Testcontainers로 200·500·timeout 혼합 수집, 정상 기사 저장, 실패 격리, 재실행 중복 0건을 검증한다.
- 범위: News API headline 수집 pipeline E2E와 base URL 설정화로 제한한다. 실제 API/RSS, flag 활성화, retry/circuit breaker, 분산 락·Kafka는 제외한다.
- 검증: 정상 category 2건 저장, 실패 category 2건 격리, 재실행 후 기사 2건·중복 그룹 0건 유지, 전체 54개 테스트와 Backend CI 통과.

### P1. 기사 URL 중복 정리 및 DB 유일성 제약 보강

- 상태: `Done` ([#210](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/210), [PR #211](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/211))
- AS-IS: #196 이후 수집 로직은 응답 내부와 DB 기존 URL을 제외하지만, 이전에 생성된 초과 중복 39건이 남아 있고 DB 자체 유일성 제약은 없다.
- TO-BE: 안전한 기존 중복을 V3로 정리하고 generated SHA-256 UNIQUE로 동일 URL 저장을 DB에서 거부한다.
- 범위: 과거 데이터 정리, DB 제약, Testcontainers 회귀 검증으로 제한한다. 실제 외부 수집, flag 변경, 분산 락·Kafka는 제외한다.
- 검증: raw SHA-256 hash 기준 중복 정리와 case variant 보존, 삭제 전 canonical schema 검증, 실패·repair·재실행을 포함한 전체 53개 테스트와 Backend CI 통과. 로컬 기사 `9853 → 9814`, 중복 `39 → 0`, scrap/chat/source 행 수 유지.

### P1. Flyway 기반 스키마 기준선 및 Testcontainers 재현성 확보

- 상태: `Done` ([#208](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/208), [PR #209](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/209))
- AS-IS: Hibernate DDL과 시작 시점 Java 설정이 스키마를 보완해 schema 변경 순서와 환경별 재현 여부를 추적하기 어렵다.
- TO-BE: V1으로 5개 도메인 테이블을 생성하고 V2로 legacy 객체 이름과 FULLTEXT를 canonical 구조로 수렴시킨 뒤 Hibernate `validate`와 Testcontainers로 자동 검증한다.
- 범위: 현재 schema 기준선과 비파괴 local baseline으로 제한한다. URL UNIQUE, 중복 정리, 기능 schema 변경, 운영 배포는 제외한다.
- 검증: 빈 MySQL V1→V2와 legacy baseline 1→V2, invalid index/FK 실패·repair·재실행을 재현했다. 기존 DB의 V2 적용 후 canonical FK·인덱스와 데이터 보존 및 전체 49개 테스트 통과를 확인했다.

### P1. develop PR Gradle·Testcontainers 자동 테스트 및 배포 workflow 분리

- 상태: `Done` ([#206](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/206), [PR #207](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/207))
- AS-IS: 기존 workflow가 `main` build/deploy에 결합되고 테스트를 `-x test`로 제외해 `develop` PR에서 회귀 검증이 자동 실행되지 않는다.
- TO-BE: secrets 없는 독립 CI에서 전체 Gradle 테스트를 실행하고, 대상 EC2가 삭제된 legacy 배포 workflow는 자동 실행 목록에서 제거한다.
- 범위: Backend CI 추가와 legacy CD workflow 제거로 제한하며 새 EC2/CD, Dockerfile·Compose, repository secrets, branch protection은 변경하지 않는다.
- 검증: 로컬 강제 재실행에서 Testcontainers를 포함한 45개 테스트가 통과했고, 첫 GitHub `Backend CI`도 1분 50초에 통과했다.

### P1. SSE query token 허용 경로 제한

- 상태: `Done` ([#204](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/204), [PR #205](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/205))
- AS-IS: JWT 필터가 모든 요청의 `token` 쿼리 파라미터를 인증 수단으로 허용해 EventSource와 무관한 URL까지 JWT 노출 범위가 넓다.
- TO-BE: Bearer JWT는 기존처럼 전역에서 허용하고, query JWT는 `GET /api/ai/{id}/ask`에서만 인증에 사용한다.
- 범위: 필터 토큰 추출 조건과 허용·차단·Bearer 회귀 테스트로 제한하며 OAuth, JWT 구조, API 응답은 변경하지 않는다.
- 검증: 허용 경로·차단 경로·Bearer 우선순위 테스트와 Testcontainers를 포함한 전체 45개 테스트가 통과했다.

### P1. MySQL Testcontainers 통합 테스트 및 트랜잭션 회귀

- 상태: `Done` ([#202](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/202), [PR #203](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/203))
- AS-IS: MySQL 원자 UPDATE와 rollback은 수동 측정 또는 mock 테스트만 있어 실제 DB 회귀를 자동으로 감지하지 못한다.
- TO-BE: 테스트 전용 MySQL 8에서 20개 동시 증가와 실패 transaction rollback을 자동 검증한다.
- 범위: Repository slice로 제한하고 개발 DB, 전체 E2E, Redis/Kafka container는 제외한다.
- 검증: viewCount가 동시 UPDATE 수 20과 일치하고 실패 transaction은 0으로 rollback되며 임시 container가 자동 정리돼야 한다.

### P1. 보호 API matcher 및 사용자 데이터 접근 통제

- 상태: `Done` ([#200](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/200), [PR #201](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/201))
- 검증: 보호 API의 미인증 요청은 401, valid JWT는 token subject userId로 접근하며 공개 API는 비로그인 접근을 유지했다.

### P1. 기사 상세 동시 조회 viewCount 원자 증가

- 상태: `Done` ([#198](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/198), [PR #199](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/199))
- 검증: 동일한 20 VU·20요청에서 성공 요청 수와 실제 조회수 증가량이 20으로 일치했다.

### P1. RSS/News API 수집 중복 방지와 재실행 안전성

- 상태: `Done` ([#196](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/196), [PR #197](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/197))
- 검증: RSS/News API 응답 내부 URL 중복 제거와 단일 인스턴스 순차 재실행을 자동 테스트로 고정했다.

### P2 follow-up. Mixed API single-instance saturation boundary

- 상태: `Done` ([#194](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/194), [PR #195](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/195))
- 검증: 120.07 business RPS까지 dropped/error 없이 처리량이 증가했다. 다만 100/120 RPS에서 Hikari pending 11/18, MySQL CPU periodic sample 152.81%/203.38%, 조회 p95 최대 174.99ms/308.08ms로 DB 자원 압박이 증가했다.

### P2 follow-up. Mixed API constant-arrival-rate single-instance baseline

- 상태: `Done` ([#192](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/192), [PR #193](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/193))
- 검증: 20/40/60 RPS 모두 목표율을 달성했고 dropped/error는 0이었다. 60 RPS 조회 p95 최대 81.59ms, Hikari pending 0, executor queue 0으로 해당 범위에서는 병목이 확인되지 않았다.

## 운영 규칙

- 모든 변경은 `Issue → Branch → PR → Merge` 순서를 따른다.
- AI는 조사와 계획을 먼저 제시하며, 사용자 승인 전에는 코드, DB, 배포 설정을 변경하지 않는다.
- 작업 범위 또는 DB 영향이 달라지면 구현을 멈추고 Issue 또는 계획을 갱신한 뒤 다시 승인받는다.
- 성능 작업은 개선 전 기준값과 개선 후 결과를 같은 측정 조건에서 기록한다.
- 새 이슈 후보를 고를 때마다 현재 프로젝트 단계, 데이터 규모, 신입 포트폴리오 설명 가능성을 기준으로 overengineering 여부를 먼저 판단한다.
- 기술적으로 가능하더라도 근거가 부족하거나 복잡도 대비 효과가 작으면 구현 대신 측정 문서, 판단 기록, ADR, 후속 적용 기준 정리로 범위를 낮춘다.
- 작업 내용, 검증 결과, docs 기록은 가능한 한 PR 본 작업 커밋에 함께 포함해 develop commit history를 이슈별 핵심 변경 중심으로 유지한다.
- merge 후 `WORK_PROGRESS.md`만 갱신하는 후처리 커밋은 기본값으로 만들지 않고, GitHub PR/Issue 상태로 merge 결과를 확인한다.

## 상태 표기

- `Backlog`: 아직 시작하지 않은 후보
- `Planned`: Issue 및 작업 계획이 준비된 상태
- `In Progress`: 승인된 브랜치에서 작업 중
- `Verified`: 테스트 또는 측정 결과까지 확인됨
- `Done`: PR 병합 완료

## P0. AI 작업 운영 규칙 및 개선 Backlog 정립

- 상태: `In Progress` ([#113](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/113), [#166](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/166))
- AS-IS: 개선 작업의 계획, 승인, AI 검토 결과가 GitHub 흐름과 일관되게 연결되어 있지 않다.
- TO-BE: 작업 계획과 승인 기준을 Issue, 브랜치, PR, 문서에 연결해 재현 가능한 작업 흐름을 만든다.
- 성공 기준:
  - 최소 1개의 후속 개선 작업이 문서화된 흐름을 따라 Issue부터 PR까지 진행된다.
  - 계획, 승인 범위, 검증 결과를 GitHub에서 확인할 수 있다.

## P1. 주요 조회 API 관측성 및 성능 기준선 확보

- 상태: `Done` ([#180](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/180), [PR #181](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/181))
- 완료 근거: [#121](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/121), [#174](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/174), [#176](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/176), [#178](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/178), [#180](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/180)
- AS-IS: 캐시 미스 시 기사 조회, 키워드 추출, 번역 API 호출, FULLTEXT 검색이 요청 경로에서 수행되지만 단계별 지연 시간과 캐시 효과를 수치로 설명할 수 없다.
- TO-BE: 캐시 히트율, 단계별 처리 시간, 외부 번역 API 호출량, p95 응답 시간을 측정하고 부하 테스트 기준선을 만든다.
- 성공 기준:
  - 캐시 히트/미스 시나리오별 p50, p95, 오류율을 기록한다.
  - 번역 API 호출 횟수와 캐시 히트율을 확인할 수 있다.
  - 측정 결과를 후속 개선 PR에 비교 기준으로 남긴다.
  - #174에서 외부 호출 없는 주요 기사 조회 API의 고부하 p95/오류율과 DB 병목 후보를 분리 측정했다.
  - #176에서 `popular` 조회의 `Using filesort`를 k6와 `EXPLAIN ANALYZE`로 확인하고, 현재 규모에서는 인덱스 추가를 보류하는 판단 기준을 남긴다.
  - #178에서 로컬 부하 테스트 시 k6 결과, 애플리케이션 latency 로그, Docker 리소스 지표를 같은 실행 구간에 맞춰 해석하는 runbook을 정리한다.
  - #180에서 로컬 Docker 단일 인스턴스 기준 `popular` 조회의 안정 처리량과 포화 신호 구간을 RPS/p95/failure로 정리한다.

## P2. 기사 원문 크롤링 안정성 개선

- 상태: `Done` ([#170](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/170), [PR #171](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/171), [#172](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/172), [PR #173](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/173))
- AS-IS: 기사 상세의 요약·질의응답 요청 중 외부 언론사 페이지를 동기 크롤링하며, 외부 사이트의 지연과 실패가 사용자 요청에 직접 영향을 준다.
- TO-BE: timeout, 실패 분류, 재시도 정책, 크롤링 결과 저장 정책을 정의하고 장애 상황에서도 예측 가능한 응답을 제공한다.
- 성공 기준:
  - 연결 지연, 읽기 지연, 본문 없음, 차단 응답의 처리 규칙이 문서화되고 검증된다.
  - 동일 기사 요청 시 불필요한 재크롤링을 줄이는 기준을 확인한다.
  - #170/#171에서 요약 API의 동기 원문 크롤링 경로를 cold/warm/fallback 조건으로 측정할 수 있는 기준선을 수립했다.
  - #172/#173에서 summary hit, crawledContent hit, cold crawl, crawler fallback, AI summary 호출 시간을 단계별 로그로 분리 관측할 수 있게 했다.

### P2 후속 후보. Gemini mock latency 부하 테스트

- 상태: `Done` ([#182](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/182), [PR #183](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/183))
- AS-IS: 기사 상세의 AI 요약/질의응답 경로에는 Gemini 외부 호출이 포함될 수 있지만, 실제 Gemini API로 부하 테스트를 반복하면 비용, quota, rate limit, 응답 편차 문제가 생긴다.
- TO-BE: mock AI 서버로 latency `200ms`, `1s`, `3s`, `timeout/5xx` 조건을 통제하고, 사용자 요청 경로의 p95/오류율/fallback 동작을 측정한다.
- 진행 조건:
  - #178의 로컬 부하 테스트 로그/리소스 캡처 기준을 먼저 적용한다.
  - mock 서버는 테스트/로컬 설정에만 사용하고 운영 API key, prompt 전문, 사용자 질문 전문은 문서/로그에 남기지 않는다.
- 예상 이슈:
  - `[PERF] AI 질의응답 Gemini 외부 호출 mock latency 부하 테스트`
- 측정 결과:
  - 정상 응답은 mock 200ms/1s/3s 조건에서 p95가 각각 약 263~290ms/1.04~1.06s/3.04~3.05s로 외부 지연을 따라갔다.
  - mock 500 조건은 43/43 요청이 backend 5xx로 전파되어, timeout/fallback 정책 검토가 별도 후속 후보로 남았다.

### P2 후속 후보. Gemini timeout 상한 및 upstream 오류 분리

- 상태: `Done` ([#184](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/184), [PR #185](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/185))
- AS-IS: article summary의 Gemini 호출은 고정 90초 timeout을 사용하고 non-2xx, timeout, 내부 파싱 오류를 모두 backend 500으로 반환한다.
- TO-BE: 10초 설정형 timeout으로 사용자 대기 상한을 줄이고, Gemini non-2xx는 502, timeout은 504, 내부 오류는 500으로 분리한다.
- 검증 결과:
  - mock 15초 조건 p95가 개선 전 16.08초에서 개선 후 10.42초로 제한됐다.
  - mock 500은 약 0.31초에 backend 502, mock 15초는 약 10.04초에 backend 504를 반환했다.
  - mock 3초 정상 응답은 약 3.09초에 backend 200으로 유지됐다.
- 범위 경계:
  - retry, circuit breaker, async queue, SSE/Trend Gemini 정책 변경은 현재 근거 대비 과해 제외한다.

### P2 후속 후보. Gemini 동기 호출 servlet thread 포화 및 API 영향 측정

- 상태: `Done` ([#186](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/186), [PR #187](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/187))
- AS-IS: #182에서 mock latency가 summary p95를 지배함을 확인했지만, 동기 `.block()` 호출이 servlet thread를 얼마나 점유하고 다른 조회 API 지연으로 전파되는지는 측정하지 않았다.
- TO-BE: 로컬 Tomcat thread 수와 Gemini mock 3초 지연을 통제하고, summary VU 증가에 따른 RPS/p95/실패율, busy thread, 동시 `popular` API p95를 같은 실행 구간에서 측정한다.
- 범위 경계:
  - 실제 포화 근거를 확보하기 전에는 async, queue, retry, circuit breaker를 구현하지 않는다.
  - 8GB 로컬 환경에서 `5 -> 10 -> 20 -> 50 VU` 순서로 진행하고 명확한 포화가 나타나면 다음 단계를 생략한다.
- 검증 결과:
  - 20 summary VU에서 Tomcat busy/current가 20/20에 도달했고 summary는 6.09 RPS, p95 3.23초였다.
  - 같은 실행에서 `popular` p95는 대조군 154.46ms에서 2.86초로 약 18.5배 증가하고 RPS는 24.55에서 3.71로 감소했다.
  - `popular` 내부 `dbQueryMs` 36~42ms, `totalMs` 68~76ms와 client p95 차이로 servlet thread 대기 전파를 확인했다.
  - 20 VU에서 포화가 명확해 50 VU는 안전 중단 기준에 따라 생략했고, async 전후 비교를 별도 후속 후보로 남긴다.

### P2 후속 후보. Gemini summary Servlet async 전환 전후 비교

- 상태: `Done` ([#188](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/188), [PR #189](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/189))
- AS-IS: 동기 20 VU에서 Tomcat thread 20/20과 popular 지연 전파를 확인했지만, 별도 worker 격리가 실제로 다른 API를 보호하는지는 검증하지 않았다.
- TO-BE: 동기 50 VU 기준선을 추가하고 `WebAsyncTask + bounded executor` 적용 후 20/50 VU를 같은 조건에서 비교한다.
- 검증 결과:
  - 동기 50 VU는 summary 6.28 RPS/p95 8.98초, popular 0.91 RPS/p95 5.97초, Tomcat busy 20/20이었다.
  - async 50 VU는 summary 6.29 RPS/p95 9.15초로 비슷했지만 popular는 22.40 RPS/p95 172.98ms, Tomcat busy peak 8/20으로 회복했다.
  - async executor는 active 20, queue peak 30이어서 병목 제거가 아니라 bounded pool 격리임을 확인했다.
  - mock 500은 502, MVC async timeout과 executor rejection은 503, 내부 오류는 500으로 분리한다.
- 범위 경계:
  - 전면 WebFlux, Kafka, retry/circuit breaker, SSE/ask/Trend 변경은 현재 근거 대비 과해 제외한다.

### P2 후속 후보. Gemini async executor 포화 503 및 queue 보호 검증

- 상태: `Done` ([#190](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/190), [PR #191](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/191))
- AS-IS: #188의 50 VU에서 executor active 20/queued 30까지 관측했지만 queue capacity 100 포화와 실제 HTTP 503 rejection/recovery 경로는 검증하지 않았다.
- TO-BE: 로컬 executor를 5 workers/queue 10으로 축소하고 점진 부하를 적용해 200/503, queue 상한, popular API 보호, 부하 종료 후 회복을 같은 실행 흐름에서 검증한다.
- 범위 경계:
  - 기본 executor 20/100 변경, rate limiter, retry/circuit breaker, Kafka/WebFlux, EC2 capacity 추정은 측정 결과 없이 진행하지 않는다.
  - 결과는 제한된 로컬 단일 인스턴스의 overload protection 검증으로 한정한다.
- 검증 결과:
  - 15 VU에서 active 5/queued 10으로 executor를 모두 사용하면서 55/55건이 200이었다.
  - 20 VU부터 실제 503이 발생했고, 20/30 VU 모두 queue는 설정 상한 10을 넘지 않았다.
  - 20 VU는 200 55건/503 1,158건, accepted p95 9.41초/rejected p95 49.55ms, popular p95 254.51ms였다.
  - 30 VU는 200 55건/503 3,720건, accepted p95 9.34초/rejected p95 34.25ms, popular p95 256.03ms였다.
  - 부하 종료 후 active/queued 0/0과 recovery 5 VU의 25/25 HTTP 200을 확인했다.

## P2-1. 검색 API FULLTEXT 성능 기준선

- 상태: `Done` ([#168](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/168), [PR #169](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/169))
- AS-IS: #133/#134에서 검색 API FULLTEXT 실행 계획과 중복 `OR MATCH` 최적화는 확인했지만, 검색어 유형별 API p95, 실패율, 결과 수 기준선은 분리되어 있지 않다.
- TO-BE: 영어/한국어/짧은 검색어/결과 적은 검색어를 같은 조건에서 측정하고, MySQL FULLTEXT 기반 검색의 안정성을 p95와 실패율로 설명한다.
- 성공 기준:
  - 검색어별 측정 스크립트가 있다.
  - p95, 실패율, 결과 수를 기록할 수 있는 문서 템플릿이 있다.
  - Elasticsearch 도입 여부는 결정하지 않고, 도입 검토 조건만 남긴다.
  - #168/#169에서 검색어별 smoke baseline을 기록했다.

## P3. 다국어 이슈 매칭 품질 개선

- 상태: `Backlog` (최근 완료: [#142](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/142), [#146](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/146), [#148](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/148), [#150](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/150), [#152](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/152), [#154](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/154), [#156](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/156), [#160](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/160), [#164](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/164))
- AS-IS: 기사 제목의 키워드와 영어 번역 키워드를 MySQL FULLTEXT 검색에 사용하므로, 표현이 다른 동일 이슈 또는 비영어권 기사 간 매칭이 누락될 수 있다.
- TO-BE: 기존 후보 검색을 유지하면서 이슈 유사도와 국가 다양성 기준으로 결과를 재정렬하는 정책을 검증한다.
- 최근 측정: #160에서 `ORDER BY published_at DESC` latest-first, FULLTEXT score 기반 relevance-first, bounded recency signal을 더한 hybrid 후보를 비교했다. 측정상 pure relevance-first는 wrong-context 기사도 끌어올릴 수 있어, 후속 코드 변경은 hybrid 후보 중심으로 검토하는 것이 안전하다.
- 다음 판단: #164에서 hybrid ranking query variant를 바로 구현하지 않는 이유와 후속 적용 기준을 docs/ADR로 정리했다. 코드 변경은 대표 샘플, 기대 top-result intent, 회귀 테스트, before/after 측정 기준이 준비된 뒤 별도 Issue로 검토한다.
- 성공 기준:
  - 대표 이슈 샘플과 기대 국가를 정의한다.
  - 개선 전후의 국가별 관련 기사 노출 수와 매칭 근거를 비교한다.
  - 데이터 규모와 비용을 고려한 기술 선택 근거를 ADR로 남긴다.

## P4. 핵심 API 회귀 테스트 기반 마련

- 상태: `Done` ([#140](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/140), [PR #141](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/141))
- AS-IS: 핵심 수집, 검색, Perspectives 경로의 정상·실패 동작을 자동 검증하는 테스트 기반이 부족하다.
- TO-BE: 외부 API를 격리한 서비스 단위 테스트와 핵심 API 통합 테스트를 단계적으로 도입한다.
- 성공 기준:
  - Perspectives 캐시 히트/미스와 번역 실패 케이스를 자동 검증한다.
  - 외부 의존성 실패 시 기대 응답을 테스트로 고정한다.
