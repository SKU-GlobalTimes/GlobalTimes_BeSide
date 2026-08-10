# Backend Improvement Next Agent Brief

이 문서는 새 Codex 세션이 `WORK_PROGRESS.md` 전체를 읽기 전에 현재 상태를 빠르게 복원하기 위한 짧은 handoff 문서다.
상세한 이력과 근거는 `WORK_PROGRESS.md`, `BACKLOG.md`, 개별 측정 문서를 기준으로 확인한다.

## Current Active Work

현재 진행 중인 backend improvement Issue는 없다.

## Recently Completed

- #245 / PR #246: `Done`
- 결과: Gemini AI 질의의 로그인 DB·익명 Redis 대화 저장 처리 뒤 named SSE `end` 이벤트를 보내고 emitter를 완료하도록 Backend–Frontend 정상 종료 계약을 명시했다.
- 검증: event builder payload와 `저장 → end → complete`, I/O·상태 오류 시 container 종료 위임을 포함한 집중 테스트 6개, 전체 103개 테스트와 Backend CI가 통과했다. AI Reviewer는 Blocking 없음·MERGE_READY로 판정했다.
- 후속: Frontend에서 정상 `end`, REST 요약 `502/503/504`, SSE 실패 재시도와 중복 연결 방지를 연동한다. 실제 Gemini, 자동 retry, WebFlux, queue·Kafka와 Issue #110은 제외했다.

- #243 / PR #244: `Done`
- 결과: Gemini·외부 호출·부하·DB·관측·수집 문서 13개에 한국어 학습 안내를 추가하고 원본 측정 기록을 보존했다. Phase 1 작업·학습 여정에서 실제 협업 절차, 작업 연대기, 문제·개념·수행·검증·다음 선택 이유를 Issue/PR 링크와 연결했다.
- 검증: 대상 문서 숫자 token·code fence 원문 대조, 상대 링크, `git diff --check`, Backend CI가 통과했다.
- 범위: production code·test·schema·workflow·신규 측정·신규 기술과 Issue #110은 제외했다.

- #241 / PR #242: `Done`
- 결과: 검색 API와 Perspectives의 영어 중심 근거 문서 10개를 한국어 중심으로 재정리했다. SQL·명령·로그·metric 식별자, 측정 수치, 표본 ID와 해석 한계는 유지했다.
- 검증: 숫자 token과 code fence 수 원문 대조, 상대 링크, `git diff --check`, Backend CI가 통과했다.
- 범위: production code·test·schema·workflow·신규 측정·신규 기술과 Issue #110은 제외했다.

- #239 / PR #240: `Done`, Backend Phase 1 `Completed`
- 결과: 14개 섹션의 구조·근거 맵으로 수집부터 CI까지 실행 경로를 문제·해결·수치·검증·원본 MD/PR/코드에 연결하고, #223 이후 정량 인덱스와 README 현재 상태를 갱신했다.
- 검증: 상대 Markdown·Java·SQL·workflow 링크 150개와 수치·코드 경로를 대조했다. 검색 API 경로 Blocking 수정 후 Backend CI 성공, AI Reviewer Blocking 없음·MERGE_READY.
- Phase 1 완료 범위: MySQL query·transaction, Redis cache·동시성, 외부 API timeout·오류·thread 격리, 수집 정합성·처리량, 인증, Flyway·Testcontainers·CI.
- Phase 2 첫 후보: Perspectives labeled sample 확대와 collection/retrieval/ranking 원인 분리. Vector DB·Kafka·scale-out 등은 구조 맵의 도입 신호를 먼저 확인한다.
- 범위 밖: 신규 측정·production code·test·schema·workflow·신규 기술과 Issue #110.

- #237 / PR #238: `Done`
- 결과: Trend scheduler의 선행 DELETE와 삭제 API를 제거하고, 새 목록 직렬화 후 Redis SET 한 번으로 기존 값을 교체하도록 변경했다.
- 검증: 정상 교체·직렬화 실패·write 실패 집중 테스트 3개, Docker 비의존 61개 테스트와 Backend CI 전체 테스트가 통과했다. AI Reviewer는 Blocking 없음·MERGE_READY로 판정했다.
- 경계: mock Redis로 DELETE 미호출과 기존 fixture 조회를 검증했으며 실제 네트워크 장애 재현, TTL·scheduler·flag 변경, Lua/MULTI·분산 락·retry·queue·Kafka·Issue #110은 제외했다.
- Phase 1은 #239/#240에서 완료했다.

- #235 / PR #236: `Done`
- 결과: 손상된 Trend Gemini prompt를 복구하고 기존 `gemini.timeout-ms`를 적용해 non-2xx 502, timeout 504, 내부 응답 처리 오류 500 계약을 고정했다.
- 캐시: cache hit는 Gemini를 생략하며 Redis read 실패 fallback과 write 실패 시 정상 summary 반환을 유지했다.
- 검증: mock HTTP·Redis 집중 테스트 7개, Docker 비의존 58개 테스트와 Backend CI 전체 테스트가 통과했고 AI Reviewer는 Blocking 없음·MERGE_READY로 판정했다.
- 범위: 실제 Gemini·async·retry·circuit breaker·queue·Kafka·캐시 정책·Issue #110은 제외했다.
- Phase 1 종료선은 위 #237 완료 결과와 잔여 작업을 기준으로 판단한다.

- #233 / PR #234: `Done`
- 결과: RSS/News API의 100·500·1,000건 신규 저장·중복 재실행과 mock upstream 순차 지연 전파를 MySQL Testcontainers에서 측정했다.
- 측정: 신규 1,000건은 News API 4,244.38ms·1,022 statements, RSS 2,860.14ms·1,003 statements였다. 재실행은 각각 53.83ms·1 statement, 56.15ms·2 statements와 저장 0건이었다.
- 지연 전파: 동일 4요청·75저장·5xx 1건에서 all-fast 506.69ms, 300ms 지연 source 포함 790.89ms, 차이 284.20ms였다. 5xx 이후 source는 계속 처리됐다.
- 판단: 4/6시간 scheduler와 겹칠 근거가 없어 Kafka·durable queue는 보류했다. 집중 4개·전체 91개 테스트와 Backend CI가 통과했고 AI Reviewer는 Blocking 수정 재검토 후 MERGE_READY로 판정했다.
- Phase 1 종료선은 위 #235 완료 결과와 잔여 순서를 기준으로 판단한다.
- 범위: 테스트·fixture·측정 문서만 변경했으며 실제 API, flag, 운영 DB, production code, schema/index, scheduler, Kafka·queue·retry·Issue #110은 제외했다.

- #231 / PR #232: `Done`
- 결과: transactional save 내부 catch와 commit 전 성공 로그를 제거하고, `AiSseService`가 transaction proxy 호출 반환 뒤 성공 또는 실패를 기록해 로그인 이력 저장 실패와 이미 전달된 SSE 답변 완료를 분리했다.
- 정책: 로그인 이력 저장은 답변 생성 후 best-effort 부가 기능이며 실패한 turn은 다음 context에서 빠지지만 현재 답변 성공은 유지한다. 익명 Redis 경로는 변경하지 않았다.
- 검증: mock SSE 4개, MySQL 저장 3개, 전체 87개 테스트와 Backend CI 통과. 실제 외부 API 호출 0회이며 AI Reviewer는 Blocking 없음·MERGE_READY로 판정했다.
- 범위: `AiSseService`, `ChatHistoryService`, mock SSE, MySQL Testcontainers와 문서만 변경했다. 실제 Gemini·retry·outbox·Kafka·queue·익명 Redis·Issue #110은 제외했다.

- #229 / PR #230: `Done`
- 결과: 동일 user/article 동시 POST toggle 20건의 성공 2·실패 18을 user 행 `PESSIMISTIC_WRITE`로 성공 20·실패 0, `true/false` 각 10건, 최종 scrap 0건으로 개선했다.
- 잠금·API 경계: 서로 다른 사용자의 같은 기사 요청은 각각 저장되며 POST toggle의 비멱등 의미는 유지했다. PUT/DELETE·프론트 변경·Redis 분산 락·Kafka·queue는 제외했다.
- 검증: 스크랩 집중 테스트 7개, 전체 80개 테스트, Backend CI 통과, AI Reviewer Blocking 없음·MERGE_READY.
- 범위: `ScrapService`, `UserRepository`, MySQL Testcontainers와 문서만 변경했으며 Issue #110은 제외했다.

- #227 / PR #228: `Done`
- 결과: 선행 작업에서 이어받은 실제 DB 고정 표본 6건에서 strict Precision@5 `0.267`, Useful Precision@5 `0.467`, Hit@5 `0.500`, 평균 반환 국가 수 `1.67`을 기록했다.
- 해석 경계: 전체 9,814건의 정확도가 아니며 8148·8468의 candidate 0건은 coverage 또는 retrieval 원인 미확정으로 유지했다.
- 번역·검증: 일일 quota 1,000자 아래에서 2회·52자만 호출했고 read-only SQL MySQL 8 실행과 Backend CI를 통과했다. AI Reviewer는 Blocking 없음·MERGE_READY로 판정했다.
- 범위: 측정·문서·read-only SQL만 변경했으며 운영 코드·schema·query·ranking·수집·번역 저장·신규 검색 기술·Issue #110은 제외했다.

- #225 / PR #226: `Done`
- Result: controlled MySQL fixtures now verify translated search, absent-data and wrong-translation zero-result conditions, original-keyword fallback, and translated/original result deduplication
- Validation: five focused scenarios, all 76 tests, and Backend CI passed; the AI Reviewer reported no Blocking and MERGE_READY
- Boundary: backend orchestration only; no claim about real translation quality, source coverage, semantic similarity, production code, schema, API, ranking, translation persistence, real external API, compose DB, new search technology, or Issue #110

- #223 / PR #224: `Done`
- Result: News API/RSS batch logs now expose source dimensions, received/invalid/duplicate/saved counts, publication range, and freshness; a read-only SQL snapshot separates local coverage gaps from matching failures
- Validation: fixtures report 4 received, 1 invalid, 2 duplicate, 1 saved, and the 09:00-11:00 UTC range; all 71 tests and Backend CI passed, and the AI Reviewer reported no Blocking and MERGE_READY
- Boundary: no real external call, populated compose DB claim, schema, translation, ranking, retry, Kafka, APM, or Issue #110 work

- #221 / PR #222: `Done`
- Result: Redis List append preserved 20/20 concurrent anonymous chat turns instead of 1/20, Sorted Set preserved 20/20 article entries, and `ZADD GT` prevented delayed older activity scores from replacing newer scores; all 67 tests and Backend CI passed
- Boundary: Redis List, Sorted Set, basic Spring Data Redis commands, and Redis Testcontainers only; no Lua, MULTI/EXEC, distributed lock, Kafka, Gemini call, logged-in MySQL chat change, or Issue #110 work

- #218 / PR #220: `Done`
- Result: consolidated domain problems, changes, quantitative outcomes, validation tools, and source evidence across k6, mock, Testcontainers, EXPLAIN, and Actuator work
- Boundary: documentation-only evidence indexing; no new measurements, production code, test, load script, technology adoption, or Issue #110 work

- #216 / PR #217: `Done`
- Result: projection batch queries reduced authenticated scrap-list SQL from 201 to 1 and legacy ID-list SQL from 200 to 1 on a synthetic 100-row MySQL fixture; all 58 tests and Backend CI passed
- Boundary: the local database has one scrap row; pagination, Redis, new indexes/Flyway, toggle concurrency, and Issue #110 remain excluded

- #214 / PR #215: `Done`
- Result: a MySQL 8 window-function projection replaced all-history Java grouping; on a synthetic 5,000-chat/100-article fixture SQL statements changed from 101 to 1, entity loads from 5,100 to 0, and all 55 tests plus Backend CI passed
- Boundary: the local database has only four chat rows, so pagination, a new index/Flyway migration, Redis, anonymous chat changes, and Issue #110 remain excluded

- #212 / PR #213: `Done`
- Result: a random-port mock upstream and MySQL Testcontainers verify 200/500/timeout isolation, two successful article saves, and zero duplicate URL groups after rerun; all 54 tests and Backend CI passed
- Boundary: real News API/RSS calls, `news-fetch.enabled=true`, retry/circuit breaker, distributed locks, Kafka, multi-instance deployment, and Issue #110 remain excluded

- #210 / PR #211: `Done`
- Result: Flyway V3 removed 39 safe duplicate article rows and enforces exact URL uniqueness with a generated SHA-256 UNIQUE index; all 53 tests and Backend CI passed
- Boundary: real News API/RSS E2E, `news-fetch.enabled`, distributed locks, Kafka, multi-instance deployment, and Issue #110 remain excluded

- #208 / PR #209: `Done`
- Result: Flyway V1/V2 reproduces the five-table schema, canonical constraints/indexes, and FULLTEXT across fresh and legacy databases

- #206 / PR #207: `Done`
- Result: every `develop` PR and push runs all Gradle tests, including MySQL Testcontainers, in an independent Backend CI workflow

- #204 / PR #205: `Done`
- Result: query JWT is accepted only on the article ask SSE route while Bearer authentication remains globally available and takes priority
- #202 / PR #203: `Done`
- Result: MySQL 8 Testcontainers verifies 20 concurrent atomic increments and transaction rollback without touching the compose database
- #200 / PR #201: `Done`
- Result: protected user/scrap/chat requests now return 401 without authentication while valid JWT subjects own service access

## Read Order

1. `docs/backend-improvement/NEXT_AGENT_BRIEF.md`
2. `docs/backend-improvement/WORK_PROGRESS.md`
3. `docs/backend-improvement/BACKLOG.md`
4. 현재 후보와 직접 관련된 측정/설계 문서

## Workflow

모든 작업은 아래 순서로 진행한다.

```text
Issue
→ Branch
→ 조사
→ 계획
→ 승인
→ 구현
→ 테스트
→ PR
→ AI Reviewer comment 또는 사용자 직접 확인
→ Blocking 확인 또는 사용자 확인
→ merge
→ GitHub PR/Issue 상태 확인
```

바로 구현하지 말고, 먼저 develop 최신화, 열린 Issue/PR 확인, `WORK_PROGRESS.md`와 `BACKLOG.md` 조사를 수행한다.
새 Issue/PR 본문은 repository template을 읽고 반드시 `--body-file` 방식으로 작성한다.
작업 내용과 관련 docs 기록은 가능한 한 PR 본 작업 커밋에 함께 포함한다.
Reviewer 이후 diff 변경을 피하기 위해 merge 후 `WORK_PROGRESS.md`만 갱신하는 후처리 커밋은 기본값으로 만들지 않는다.
운영 코드, DB schema/index, API 응답, Repository query, Redis/cache 정책, k6/script 변경이 없는 순수 측정 결과/판단 문서 PR은 Reviewer를 생략하고 사용자 직접 확인 후 merge할 수 있다.
반대로 코드, 스크립트, DB, cache 정책 변경이 있거나 민감 정보 노출 위험이 있으면 Reviewer 검토를 받는다.

## Overengineering Guardrail

새 이슈 후보를 제안할 때는 구현 가능성만 보지 말고, 먼저 과한 구현인지 함께 판단한다.

매번 아래 질문에 답한 뒤 Issue 범위를 정한다.

- 현재 데이터 규모와 사용자 흐름에서 지금 필요한가?
- 신입 백엔드 포트폴리오 관점에서 설명 가능한 복잡도인가?
- 기존 MySQL FULLTEXT, Redis, Spring 코드 안에서 더 단순하게 검증할 방법이 있는가?
- 코드 변경 없이 측정/문서화/ADR로 남기는 편이 더 성숙한 판단인가?
- 운영 API 응답이나 DB schema를 바꿀 만큼 충분한 근거가 있는가?

기술적으로 가능해도 근거가 부족하면 바로 구현하지 않는다.
이 경우 `측정`, `판단 기록`, `ADR`, `후속 적용 기준 정리` 같은 문서 작업으로 낮춰 잡는다.

## Current Snapshot

- Active work override, 2026-07-13:
  - #180/#181 is merged and closed. The single-instance popular baseline concluded that 20 VU was stable at about 87 RPS and 50 VU was a saturation signal.
  - #182/#183 is merged and closed. Controlled 200ms/1s/3s and 500-response measurements established the Gemini mock baseline without real API cost.
  - #184/#185 is merged and closed. The fixed 90-second summary timeout was replaced with configurable 10 seconds, with Gemini non-2xx as 502, timeout as 504, and internal parsing errors as 500.
  - #186/#187 is merged and closed. At 20 summary VUs, Tomcat reached 20/20 busy threads and concurrent popular p95 rose from the 154ms control to 2.86 seconds.
  - #188/#189 is merged and closed. It added a synchronous 50 VU baseline and a `WebAsyncTask + bounded executor` comparison at 20/50 VU.
  - At 50 VU, popular p95 changed from 5.97 seconds synchronous to 173ms async while summary stayed near 6.29 RPS and 9 seconds p95.
  - Tomcat busy peak changed from 20 to 8; the async executor reached 20 active and 30 queued, so the bottleneck was isolated rather than removed.
  - #190/#191 is merged and closed. It verified real HTTP 503 rejection, bounded queue protection, and recovery with a locally reduced executor.
  - With a local 5-worker/queue-10 executor, 15 VU filled the queue without rejection and 20 VU was the first tested stage to return real HTTP 503 responses.
  - At 20/30 VU the queue stayed at 10, accepted summary throughput stayed near 1.57 RPS, and popular p95 stayed near 255ms; a later 5 VU recovery returned 25/25 HTTP 200.

- Repo: `SKU-GlobalTimes/GlobalTimes_BeSide`
- Base branch: `develop`
- Long-running open issue: #110 `[Troubleshooting] 서비스 설계의 근본적 한계`
- #110은 사용자가 별도로 지시하기 전까지 구현하거나 정리하지 않는다.
- #158/#159에서 다음 세션 handoff 문서 정리를 완료했다.
- #160/#161에서 Perspectives FULLTEXT relevance-first/hybrid ordering 비교를 완료했다.
- #162/#163에서 새 이슈 후보마다 overengineering 여부를 먼저 판단하는 guardrail을 추가했다.
- #164/#165에서 hybrid ranking query variant를 바로 구현하지 않는 이유와 후속 적용 기준을 docs/ADR로 정리했다.
- #166/#167에서 PR 단위 문서 기록과 develop 커밋 이력 정리 기준을 문서화했다.
- #168/#169에서 검색 API FULLTEXT 검색어별 성능 기준선 수립을 완료했다.
- #170/#171에서 기사 원문 크롤링 동기 외부 호출 응답 지연 기준선 수립을 완료했다.
- #172/#173에서 기사 요약 API 외부 호출 단계별 latency 로그 추가를 완료했다.
- #174/#175에서 주요 기사 조회 API 고부하 부하 테스트 및 DB 병목 기준선 수립을 완료했다.
- #176/#177에서 `articles popular` 조회의 `Using filesort`를 k6 고부하 지표와 MySQL `EXPLAIN ANALYZE`로 확인하고, 현재 데이터 규모에서는 인덱스 추가를 보류했다.
- #178/#179에서 로컬 부하 테스트 시 k6 결과, 애플리케이션 latency 로그, Docker 리소스 지표를 같은 실행 구간에 묶어 해석하는 runbook을 완료했다.
- #180/#181에서 주요 조회 API 단일 인스턴스 TPS 한계와 병목 지점 정리를 완료했다.

## Recent Completed Work

- #148/#149: `KeywordExtractor` 현행 정책을 회귀 테스트로 고정하고, 현재 정책이 의미 유사도 검색이 아니라 제목 토큰 기반 후보 탐색임을 명확히 했다.
- #150/#151: MySQL FULLTEXT BOOLEAN MODE 검색어 포맷을 `+first +second third` 형태의 단일 공백 조립으로 정규화했다.
- #152/#153: `First`, `round`, `Entre` 같은 샘플 기반 일반 토큰을 필터링해 weak keyword 후보를 줄였다.
- #154/#155: RSS/News API 수집 편차와 source coverage 한계가 FULLTEXT/향후 연관도 측정 해석에 주는 영향을 별도 LOG로 남겼다.
- #156/#157: #152 전후 DB-level FULLTEXT 결과 변화를 측정했다.
- #158/#159: 긴 `WORK_PROGRESS.md`를 보완하기 위한 다음 세션 handoff 문서를 추가했다.
- #160/#161: Perspectives FULLTEXT ordering을 latest-first, relevance-first, hybrid 후보로 비교했다.
- #162/#163: 새 후보마다 overengineering 여부를 먼저 판단하는 guardrail을 추가했다.
- #164/#165: Perspectives ranking policy 도입 보류와 hybrid 후보 적용 기준을 ADR로 정리했다.
- #166/#167: PR 본 작업 커밋에 docs 기록을 함께 포함하고 merge 후 후처리 커밋을 기본값으로 만들지 않는 기준을 정리했다.
- #168/#169: 검색 API FULLTEXT 검색어별 p95, 실패율, 결과 수 smoke 기준선을 수립했다.
- #170/#171: 기사 요약 API 동기 원문 크롤링 경로의 summary-hit p95/fallback 기준선을 수립했다.
- #172/#173: 기사 요약 API의 summary hit, crawledContent hit, cold crawl, crawler fallback, AI summary 단계별 latency 로그를 추가했다.
- #174/#175: 주요 기사 조회 API의 고부하 p95/오류율과 DB 병목 후보를 분리 측정하는 k6 기준선을 수립했다.
- #176/#177: `popular` 조회의 20/50/100 VU p95와 MySQL `EXPLAIN ANALYZE`를 비교해 인덱스 추가를 보류하고 관측성 보강 필요성을 남겼다.
- #178/#179: 로컬 부하 테스트의 k6 결과, 애플리케이션 latency 로그, Docker/local resource 지표를 같은 실행 구간에 묶는 runbook을 정리했다.

## Why #160 Matters

#156에서 대표 샘플 8146은 키워드가 `+First +round Iran talks`에서 `+Iran +talks ends encouraging`로 바뀌며 상위 결과가 스포츠/라운드 노이즈에서 Iran/US talks 중심으로 이동했다.
다만 현재 쿼리는 여전히 `ORDER BY published_at DESC` 최신순이라 Lebanon/ceasefire 같은 인접 노이즈가 남는다.

#160에서는 동일 후보군에서 latest-first, relevance-first, hybrid 후보를 비교했다.
pure relevance-first는 wrong-context 기사를 끌어올릴 수 있어 바로 적용하기 위험하고, hybrid는 폐기하지 않되 후속 실험 기준이 필요한 후보로 남겼다.

## Recommended Next Backend Issue

현재 진행 중인 backend improvement Issue는 없다.

```text
다음 후보는 develop 최신화와 열린 Issue/PR 재확인 후 선정
```

최근 완료:

- 로컬 executor를 5 workers/queue 10으로 축소해 높은 VU 없이 실제 rejection 경계를 재현한다.
- summary 200/503, executor active/queued, Tomcat busy, popular p95를 같은 구간에서 기록한다.
- 부하 종료 후 queue 감소와 정상 summary 응답 회복을 확인한다.
- 기본 20/100 설정은 결과와 별도 승인 없이 변경하지 않는다.

측정은 완료됐다. 20/30 VU에서 초과 요청은 503으로 빠르게 거절되고 queue는 10을 넘지 않았으며, 부하 종료 후 active/queued 0/0과 25/25 정상 응답 회복을 확인했다.
높은 503 비율은 fast rejection 뒤 0.1초마다 재요청하는 closed-model 특성이므로 운영 실패율로 일반화하지 않는다.

현재 측정에서는 async 50 VU에서 summary 자체 처리량/p95는 개선되지 않았지만 popular p95가 5.97초에서 173ms로 감소했고 Tomcat busy peak가 20에서 8로 줄었다.
executor active 20/queued 30을 함께 기록했으므로 완전한 non-blocking 또는 병목 제거로 과장하지 않는다.

다음 세션에서 새 후보를 고를 때는 먼저 develop 최신화, 열린 Issue/PR 확인, `WORK_PROGRESS.md`와 `BACKLOG.md` 확인을 다시 수행한다.
#110은 사용자가 별도로 지시하기 전까지 다루지 않는다.
#162 guardrail에 따라 "지금 구현하면 과한가?"를 먼저 판단한다.

#164 ADR 기준상 hybrid ranking code experiment는 아래 조건이 준비된 뒤 별도 Issue로 검토한다.

```text
대표 샘플, 기대 top-result intent, 회귀 테스트, before/after 측정 기준
```

그 전까지는 P1/P2/P3 로드맵 중 더 작고 설명 가능한 측정/문서화/테스트 후보를 우선 검토한다.

판단 기준:

- 지금 구현하면 복잡도 대비 설명 가능한 효과가 있는가?
- API 응답 변경 없이 문서/ADR로 보류 판단을 남기는 편이 더 적절한가?
- 나중에 hybrid ranking을 적용하려면 어떤 샘플, 테스트, API 기준선이 필요한가?
- source coverage 한계를 ranking 문제로 오판하지 않는가?

참고 문서:

- `docs/backend-improvement/search-fulltext-analysis.md`
- `docs/backend-improvement/search-fulltext-term-baseline.md`
- `docs/backend-improvement/article-crawl-latency-baseline.md`
- `docs/backend-improvement/articles-read-high-load-db-baseline.md`
- `docs/backend-improvement/articles-popular-filesort-analysis.md`
- `docs/backend-improvement/local-load-observability-runbook.md`
- `docs/backend-improvement/single-instance-tps-baseline.md`
- `docs/backend-improvement/load-test-baseline.md`
- `docs/backend-improvement/gemini-executor-overload-protection.md`
- `docs/backend-improvement/perspectives-fulltext-generic-token-filter-result.md`
- `docs/backend-improvement/perspectives-fulltext-ordering-comparison.md`
- `docs/backend-improvement/perspectives-ranking-policy-adr.md`
- `docs/backend-improvement/perspectives-matching-sample-snapshot.md`
- `docs/backend-improvement/perspectives-source-coverage-limit-log.md`
- `docs/backend-improvement/perspectives-matching-quality-baseline.md`

## Handoff Design Note

긴 `WORK_PROGRESS.md`는 전체 audit log 역할을 하므로 유지한다.
이 문서는 새 세션의 context 비용을 줄이기 위한 index 역할만 한다.

다음 에이전트가 더 적은 context로도 진행도를 놓치지 않게 하려면 다음 원칙을 지킨다.

- 이 문서에는 최신 상태와 다음 판단만 남긴다.
- 세부 측정값, Reviewer 결과, merge 이력은 `WORK_PROGRESS.md`와 개별 문서에 남긴다.
- 새 작업을 시작할 때 이 문서의 `Current Snapshot`과 `Recommended Next Backend Issue`를 갱신한다.
- merge 후 상태 확인은 GitHub PR/Issue 상태를 기준으로 하고, 별도 후처리 커밋은 예외 상황에만 만든다.
- 의사결정 근거를 압축하되, 근거 문서 링크는 반드시 남긴다.

## Reviewer Agent Prompt Template

```text
너는 Reviewer Agent다.
코드 수정은 하지 말고 PR diff만 검토해줘.

Repo: SKU-GlobalTimes/GlobalTimes_BeSide
PR: <PR URL>
Issue: #<ISSUE NUMBER>

검토 관점:
1. 이번 PR이 Issue 범위에 머무르는가?
2. 변경 사항이 기존 측정 문서와 workflow 문서의 판단 기준과 충돌하지 않는가?
3. overengineering guardrail에 따라 구현/문서화/측정 범위가 적절히 잡혔는가?
4. NEXT_AGENT_BRIEF.md, WORK_PROGRESS.md, BACKLOG.md가 Issue 목적과 진행 상태를 정확히 반영하는가?
5. 코드, API 응답, DB schema, Redis 정책, FULLTEXT query 변경 여부가 PR 범위에 맞게 명확한가?
6. 민감 정보가 포함되어 있지 않은가?
7. 새 Blocking이 있는가?

제약:
- 코드 직접 수정 금지
- 커밋, push, merge 금지
- 리뷰 결과는 ## AI Reviewer 검토 결과 제목으로 GitHub PR comment에 직접 남겨줘.
```
