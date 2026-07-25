# Backend Phase 1 작업·학습 여정

## 1. 이 문서를 읽는 방법

이 문서는 Phase 1의 Issue를 단순히 커밋 시간순으로 나열하지 않는다. 앞선 작업에서 무엇을 확인했고, 그 결과 왜 다음 작업이 필요해졌는지를 다음 순서로 연결한다.

```text
문제 또는 의문
→ 재현·측정에 필요한 개념
→ 실제로 수행한 작은 변경
→ 테스트·수치로 확인한 결과
→ 다음 단계로 이어진 이유
```

도메인별 현재 코드 구조는 [Backend Phase 1 구조·근거 맵](backend-phase1-evidence-map.md), 개선 전후 수치는 [정량 근거 인덱스](backend-improvement-quantitative-index.md), 개별 Issue의 상세 이력은 [WORK_PROGRESS](WORK_PROGRESS.md)를 함께 본다.

## 2. 실제 협업 방식과 작업 연대기

### 2.1 Implementer·Reviewer 역할

실제 작업은 다음 절차를 반복했다.

```text
사용자와 다음 Issue 후보·과잉 구현 여부 판단
→ Implementer가 Issue·branch 생성
→ 코드·데이터·기존 문서 조사
→ 계획 승인
→ 구현과 테스트·측정
→ 단일 Issue commit과 PR
→ Reviewer가 최신 PR diff만 독립 검토
→ `## AI Reviewer 검토 결과` comment
→ Blocking 수정과 재검토
→ 사용자 merge 승인
→ squash merge
→ WORK_PROGRESS·BACKLOG·NEXT_AGENT_BRIEF 상태 확정
```

Implementer는 조사·구현·검증과 PR 작성까지 담당했다. Reviewer는 별도 대화에서 PR diff만 보고 기능·수치·해석 경계의 Blocking 여부와 `MERGE_READY`를 comment로 남겼다. merge 결정은 Reviewer가 아니라 사용자가 승인했다.

이 흐름을 문서 규칙으로 고정한 작업은 [Issue #144](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/144)와 [PR #145](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/145)다. Issue별 작업과 진척 문서를 한 squash commit에 정리하는 기준은 [Issue #166](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/166)과 [PR #167](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/167)에서 정리했다.

문서 전용 번역·링크 정리처럼 운영 동작과 기술 결정을 바꾸지 않는 작업은 자동 대조와 CI가 충분하면 Reviewer를 생략할 수 있다. ADR, 측정 해석, production code·test·schema·workflow 변경은 Reviewer 검토 대상으로 유지한다.

### 2.2 실제 작업 구간

아래 표는 GitHub에서 실제로 진행한 순서를 압축한 지도다. 각 구간 안의 모든 Issue·PR과 Reviewer 수정 내용은 [WORK_PROGRESS](WORK_PROGRESS.md)에 최신 작업부터 역순으로 기록돼 있다.

| 실제 순서 | 주요 질문 | 대표 Issue/PR | 다음 구간으로 이어진 이유 |
| --- | --- | --- | --- |
| 기준선과 즉시 오류 수정 | 주요 API는 어느 정도 응답하며 검색 오류는 어디서 발생하는가? | [#121](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/121)/[PR #122](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/122), [#123](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/123)/[PR #126](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/126) | HTTP 오류를 먼저 제거한 뒤 cache와 DB query를 분리 측정할 수 있었다. |
| Redis cache와 FULLTEXT 원인 분리 | Perspectives cold/warm 차이와 검색 query 비용은 무엇인가? | [#127](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/127)/[PR #128](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/128), [#129](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/129)/[PR #130](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/130), [#131](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/131)/[PR #132](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/132) | Query 속도만으로 관련 기사 품질을 설명할 수 없어 표본 기반 품질 분석으로 이동했다. |
| 검색 품질과 작업 규칙 | 키워드·정렬·수집 범위 중 무엇이 매칭 품질을 제한하는가? | [#142](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/142)/[PR #143](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/143), [#152](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/152)/[PR #153](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/153), [#160](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/160)/[PR #161](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/161), [#164](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/164)/[PR #165](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/165) | Pure relevance-first의 wrong-context 위험을 확인해 기술 도입보다 적용 기준을 먼저 남겼다. |
| 부하 측정의 수치화 | VU 증가에서 처리량 정체와 app·DB 자원 압박을 어떻게 구분하는가? | [#168](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/168)/[PR #169](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/169), [#174](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/174)/[PR #175](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/175), [#176](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/176)/[PR #177](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/177), [#180](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/180)/[PR #181](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/181) | DB 정렬만으로 전체 지연을 설명할 수 없어 외부 호출과 thread 포화를 별도로 재현했다. |
| Gemini 외부 호출 보호 | 느린 외부 호출이 servlet thread와 다른 API에 어떤 영향을 주는가? | [#182](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/182)/[PR #183](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/183), [#184](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/184)/[PR #185](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/185), [#186](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/186)/[PR #187](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/187), [#188](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/188)/[PR #189](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/189), [#190](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/190)/[PR #191](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/191) | Async 격리 후에도 executor 용량은 유한하므로 queue 포화와 빠른 거절·회복을 검증했다. |
| 혼합 트래픽과 핵심 정합성 | 여러 API가 함께 도착할 때 포화는 어디서 시작하고, 동시 쓰기는 안전한가? | [#192](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/192)/[PR #193](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/193), [#194](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/194)/[PR #195](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/195), [#198](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/198)/[PR #199](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/199) | 성능과 별개로 lost update·인증·transaction 회귀를 자동 검증할 필요가 드러났다. |
| 수집·인증·schema 재현 | 재실행 중복, 부분 실패, 접근 통제와 DB 구조를 어떻게 반복 검증하는가? | [#196](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/196)/[PR #197](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/197), [#200](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/200)/[PR #201](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/201), [#202](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/202)/[PR #203](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/203), [#208](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/208)/[PR #209](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/209), [#212](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/212)/[PR #213](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/213) | Testcontainers·Flyway·CI 기반이 생겨 실제 MySQL·Redis 경계의 회귀 테스트를 확장할 수 있었다. |
| Query·Redis 동시성과 품질 근거 확장 | 실제 SQL 증폭과 동시 갱신 유실을 어떻게 줄이고 검색 실패 원인을 어떻게 고정하는가? | [#214](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/214)/[PR #215](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/215), [#216](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/216)/[PR #217](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/217), [#221](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/221)/[PR #222](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/222), [#225](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/225)/[PR #226](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/226), [#227](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/227)/[PR #228](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/228) | N+1·Redis overwrite를 제거하고 labeled quality metric을 남겨 Phase 1 종료 판단의 근거가 모였다. |
| 실패 복구와 Phase 1 종료 | 부가 저장·Trend 갱신 실패를 격리하고 전체 근거를 어떻게 학습 가능한 상태로 닫는가? | [#231](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/231)/[PR #232](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/232), [#233](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/233)/[PR #234](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/234), [#235](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/235)/[PR #236](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/236), [#237](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/237)/[PR #238](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/238), [#239](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/239)/[PR #240](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/240) | 주요 경계가 코드·테스트·수치·문서로 연결돼 Phase 1을 완료했다. |

이 표는 학습 전환점을 보여주기 위한 압축본이다. 중간의 문서·ADR·측정 Issue를 생략했다는 뜻이 아니며, 정확한 commit 순서를 확인하려면 `develop` Git history와 `WORK_PROGRESS.md`를 기준으로 한다.

## 3. 출발점: 기술 도입보다 현재 구조를 측정하기

처음 목표는 Kafka, Vector DB, 분산 락 같은 기술을 추가하는 것이 아니었다. 로컬 단일 인스턴스와 약 9천 건의 기사 데이터에서 실제 병목과 정합성 문제가 어디에 있는지 설명할 수 있어야 했다.

필요했던 핵심 개념:

- **latency**: 요청 하나가 끝날 때까지 걸린 시간
- **throughput/RPS**: 일정 시간 동안 완료한 요청 수
- **VU**: 응답을 기다린 뒤 다음 요청을 보내는 가상 사용자 수
- **arrival-rate**: 응답 완료와 무관하게 목표 요청 도착률을 유지하는 부하 모델
- **p95**: 요청의 95%가 완료되는 응답시간 경계
- **기준선**: 개선 전후를 같은 조건에서 비교하기 위한 실행 환경과 수치

먼저 [부하 테스트 기준선](load-test-baseline.md)과 [단일 인스턴스 TPS 기준선](single-instance-tps-baseline.md)을 만들었다. 이후 [mixed arrival-rate 기준선](mixed-arrival-rate-baseline.md)과 [포화 경계](mixed-saturation-boundary.md)로 여러 API가 섞인 상황에서 애플리케이션, DB CPU, Hikari connection pool을 함께 관찰했다.

이 단계에서 얻은 결론은 “수천 명의 사용자를 흉내 냈다”가 아니라 **고정되지 않은 8GB 로컬 환경에서 어느 요청률부터 지연과 자원 압박이 함께 증가하는지 찾았다**는 것이다. 이 한계 때문에 결과를 운영 capacity로 확대 해석하지 않고, 이후 DB와 외부 호출을 분리 측정했다.

## 4. HTTP 지연과 DB 쿼리 시간을 분리하기

부하가 느려졌다는 사실만으로 인덱스를 추가할 수는 없다. 전체 HTTP 시간 중 DB가 실제로 얼마나 차지하는지, MySQL이 어떤 실행 계획을 선택하는지 확인해야 했다.

필요했던 핵심 개념:

- **EXPLAIN / EXPLAIN ANALYZE**: 예상 실행 계획과 실제 행 수·시간 확인
- **index scan / full table scan**: 인덱스 범위 조회와 전체 행 탐색의 차이
- **Using filesort**: 정렬용 인덱스를 그대로 사용하지 못했다는 실행 계획 표시이며, 그 자체가 병목이라는 뜻은 아님
- **N+1**: 한 번의 목록 조회 뒤 연관 데이터를 행마다 추가 조회하는 문제
- **projection**: 필요한 컬럼만 조회해 entity와 lazy relation 적재를 줄이는 방식

[인기 기사 filesort 분석](articles-popular-filesort-analysis.md)은 `Using filesort`를 발견했지만 현재 데이터 규모에서 DB query보다 HTTP·애플리케이션 비용이 더 컸기 때문에 즉시 index를 추가하지 않았다. [기사 조회 고부하 DB 기준선](articles-read-high-load-db-baseline.md)도 같은 방식으로 app latency와 DB·pool pressure를 분리했다.

반면 실제 SQL 증폭이 확인된 조회는 코드로 개선했다. [채팅 최신 이력 조회](chat-history-latest-query.md)는 모든 이력을 Java에서 묶던 구조를 MySQL window function projection으로 바꿨고, [스크랩 목록 조회](scrap-list-query-optimization.md)는 행마다 기사를 조회하던 흐름을 일괄 projection으로 바꿨다.

다음 단계는 단순 조회 성능이 아니라 동시에 쓰기가 발생할 때의 데이터 정합성이었다.

## 5. 트랜잭션과 동시성으로 데이터 정합성 고정하기

단일 서버에서도 여러 HTTP thread가 같은 DB 행을 동시에 변경할 수 있다. scheduler가 하나라는 사실과 사용자 요청의 동시성은 별개다.

필요했던 핵심 개념:

- **transaction commit**: transaction 안의 변경을 DB에 최종 반영하는 시점
- **rollback**: 예외가 발생했을 때 transaction 변경을 취소하는 동작
- **원자 연산**: 중간 상태가 다른 요청에 노출되지 않는 하나의 DB 연산
- **pessimistic lock**: 충돌 가능성이 높은 행을 먼저 잠그고 순차 처리하는 방식
- **unique constraint**: 애플리케이션 검사와 별개로 DB가 중복 저장을 최종 차단하는 규칙

[조회수 원자 증가](atomic-view-count-consistency.md)는 read-modify-write를 DB update 한 문장으로 바꿨다. [스크랩 toggle 동시성](scrap-toggle-concurrency.md)은 동일 사용자·기사 요청을 user 행 lock으로 직렬화했다. 서로 다른 사용자는 같은 기사를 스크랩할 수 있으므로 article 전체를 잠그거나 Redis 분산 락을 추가하지 않았다.

[기사 URL 유일성](article-url-uniqueness.md)은 수집 전 중복 검사만 믿지 않고 Flyway migration으로 기존 중복을 정리한 뒤 DB unique index를 추가했다. 이 작업은 멀티 인스턴스를 미리 구현한 것이 아니라 현재와 향후 어느 경로에서 쓰더라도 DB가 지켜야 할 invariant를 고정한 것이다.

## 6. 외부 API 지연을 mock으로 분리하고 thread 포화를 재현하기

기사 본문 크롤링과 Gemini 호출은 외부 서버 응답을 기다린다. 실제 Gemini를 반복 호출하면 요금과 rate limit이 측정을 왜곡하므로 응답 지연과 오류를 통제하는 mock server가 필요했다.

필요했던 핵심 개념:

- **mock upstream**: 외부 API 대신 정해진 latency·status·body를 반환하는 별도 HTTP server
- **timeout**: 외부 응답을 기다릴 최대 시간
- **servlet thread**: 동기 요청 하나를 처리하며 외부 호출 대기 중에도 점유되는 실행 단위
- **async executor**: 긴 외부 작업을 servlet thread와 분리해 실행하는 thread pool
- **bounded queue / rejection**: executor 용량을 넘는 작업을 무한 적재하지 않고 빠르게 거절하는 보호 정책
- **502/503/504**: upstream 오류, 서버 처리 용량 초과, upstream timeout을 구분한 HTTP 계약

[기사 crawl latency 기준선](article-crawl-latency-baseline.md)과 [Gemini mock latency 기준선](gemini-mock-latency-baseline.md)에서 실제 외부 비용 없이 지연을 재현했다. [Gemini timeout·오류 정책](gemini-timeout-upstream-error-policy.md)은 실패 원인을 HTTP 상태로 분리했다.

이후 [servlet thread 포화 기준선](gemini-servlet-thread-saturation-baseline.md)에서 동기 외부 호출이 요청 thread를 점유하는 현상을 확인하고, [async 전후 비교](gemini-servlet-async-comparison.md)로 servlet thread 반환과 전체 작업 완료를 구분했다. 비동기만으로 외부 API 처리량이 무한히 늘어나는 것은 아니므로 [executor overload 보호](gemini-executor-overload-protection.md)에서 제한된 pool·queue와 503 거절 정책을 고정했다.

즉 비동기 전환의 목적은 외부 API를 빠르게 만드는 것이 아니라 servlet 자원을 격리하고, 포화 시 서버 전체가 느려지는 대신 명시적으로 보호하는 것이다.

## 7. Redis는 빠른 저장소가 아니라 정합성 경계로 보기

Redis를 적용했다고 해서 동시 갱신이 자동으로 안전해지는 것은 아니다. 값을 읽고 JSON 전체를 수정한 뒤 다시 SET하면 두 요청이 서로의 변경을 덮어쓸 수 있다.

필요했던 핵심 개념:

- **cache hit/miss**: 계산 결과를 재사용하거나 원본 경로에서 다시 계산하는 조건
- **TTL**: 캐시가 자동 만료되기까지의 시간
- **stale data**: 원본이 바뀌었지만 TTL 동안 남아 있는 캐시 결과
- **Redis List / Sorted Set**: 전체 JSON 교체 대신 항목 추가와 최신순 index를 표현하는 자료구조
- **best-effort**: 부가 저장 실패가 이미 성공한 핵심 응답을 뒤집지 않게 하는 정책

[Perspectives Redis 정책](perspectives-redis-cache-policy.md)은 MySQL을 source of truth로 유지하고 제한된 stale cache를 허용했다. [익명 채팅 Redis 동시성](anonymous-chat-redis-concurrency.md)은 List와 Sorted Set 명령으로 전체 JSON 덮어쓰기를 제거했다. [Trend Redis 갱신 보존](trend-redis-refresh-preservation.md)은 새 데이터 저장 전 기존 key를 삭제하지 않아 갱신 실패 시 이전 정상 값을 유지했다.

Lua나 MULTI/EXEC는 현재 필요한 단일 명령 보장보다 복잡하므로 도입하지 않았다. multi-instance 경쟁과 여러 명령의 부분 실패가 실제로 재현될 때 다시 검토한다.

## 8. 수집 파이프라인의 재실행·실패·처리량을 구분하기

RSS와 News API 수집에서는 “한 번 성공했다”보다 같은 입력을 다시 처리해도 중복이 생기지 않는지, 한 출처 실패가 다음 출처를 막지 않는지, 배치가 scheduler 주기 안에 끝나는지가 중요했다.

필요했던 핵심 개념:

- **idempotency**: 같은 입력을 다시 처리해도 추가 부작용이 생기지 않는 성질
- **partial failure isolation**: 일부 source 실패 후에도 나머지 처리를 계속하는 경계
- **fixture**: 경계 조건을 결정적으로 재현하기 위한 테스트 데이터
- **Testcontainers**: 테스트 중 실제 MySQL·Redis Docker container를 임시 실행하는 방식
- **backlog / replay**: 처리하지 못한 작업의 누적과 재처리 요구

[News API 실패 E2E](news-api-ingestion-failure-e2e.md)는 mock HTTP와 실제 MySQL Testcontainers로 성공·오류·timeout·재실행을 확인했다. [수집 freshness·coverage 기준선](collection-freshness-coverage-baseline.md)은 source별 수집량과 발행 시각을 관찰할 수 있게 했다. [수집 batch 처리량 기준선](collection-batch-throughput-baseline.md)은 신규·중복 재실행과 느린 source의 지연 전파를 측정했다.

현재 batch가 4/6시간 scheduler 주기와 겹칠 근거가 없었기 때문에 Kafka를 도입하지 않았다. 지속적인 backlog, replay, 독립 consumer scale-out 요구가 확인될 때 queue 기술을 비교한다.

## 9. 검색 성능과 검색 품질을 분리하기

FULLTEXT query가 빠르다는 사실은 관련 기사를 잘 찾는다는 뜻이 아니다. 반대로 결과가 없다는 사실도 query 알고리즘만의 실패라고 단정할 수 없다.

필요했던 핵심 개념:

- **retrieval**: 후보 기사를 찾는 단계
- **ranking**: 찾은 후보의 순서를 정하는 단계
- **Precision@k / Hit@k**: 상위 결과의 관련성과 최소 한 건 적중 여부
- **source coverage**: 관련 기사가 실제 수집 DB에 존재하는 범위
- **labeled sample**: 기대 관련 기사 ID를 사람이 판정해 고정한 평가 표본

[Perspectives 표본 snapshot](perspectives-matching-sample-snapshot.md)에서 일반 token과 최신순 잡음을 발견했다. [일반 token filter 결과](perspectives-fulltext-generic-token-filter-result.md)는 키워드 개선 전후 DB 후보를 비교했다. [정렬 비교](perspectives-fulltext-ordering-comparison.md)에서는 pure relevance-first가 wrong-context 기사를 올릴 수 있어 즉시 적용하지 않았고 [ranking 정책 ADR](perspectives-ranking-policy-adr.md)에 hybrid 도입 조건을 남겼다.

[다국어 FULLTEXT 통합 검증](perspectives-multilingual-fulltext-integration.md)은 mock 번역과 실제 MySQL로 orchestration을 검증했지만 실제 번역 품질을 주장하지 않았다. [라벨 품질 기준선](perspectives-labeled-quality-baseline.md)은 소수 고정 표본으로 Precision@5와 Hit@5를 기록했고, candidate가 없는 사례는 collection과 retrieval 원인 미확정으로 남겼다.

따라서 Phase 2의 첫 후보는 Vector DB 구현이 아니라 라벨 표본 확대와 collection/retrieval/ranking 원인 분리다.

## 10. 실패 복구와 transaction 경계 확인하기

외부 응답 생성이 성공한 뒤 부가 DB 저장이 실패할 수 있다. transaction 메서드 안에서 예외를 catch하면 commit 시점 실패를 놓치거나 rollback 의도를 흐릴 수 있다.

[SSE 채팅 이력 저장 경계](chat-history-sse-persistence-boundary.md)는 transaction proxy 호출이 반환된 뒤 저장 성공·실패를 판단하게 했다. 로그인 채팅 저장은 다음 대화 context를 위한 부가 기능이므로 실패한 turn은 이후 context에서 빠지지만 이미 전달한 SSE 답변은 성공으로 유지한다.

이 작업을 통해 **핵심 사용자 응답**, **부가 저장**, **다음 요청의 데이터 일관성**을 하나의 성공·실패로 뭉치지 않고 정책으로 구분했다.

## 11. Flyway·Testcontainers·CI로 재현 가능하게 만들기

로컬에서 한 번 확인한 결과만으로는 이후 변경의 회귀를 막을 수 없다.

필요했던 핵심 개념:

- **Flyway migration**: schema 변경을 `V1`, `V2`, `V3`처럼 순서가 있는 SQL로 재현
- **regression test**: 이미 해결한 동작이 다음 변경에서 다시 깨지는지 확인
- **CI runner**: PR과 push마다 독립 환경에서 checkout·build·test를 수행하는 가상 실행 환경

[Flyway schema 기준선](flyway-schema-baseline.md)은 fresh DB와 legacy DB가 같은 구조에 도달하게 했고, Testcontainers가 실제 MySQL 제약·FULLTEXT·transaction을 검증하게 했다. [Backend CI](../../.github/workflows/backend-ci.yml)는 `develop` PR과 push마다 전체 Gradle 테스트를 실행한다.

현재 삭제된 EC2를 대상으로 한 legacy CD와 현재 CI는 구분한다. CI 성공은 배포 성공이 아니라 같은 코드와 테스트가 독립 runner에서도 재현됐다는 의미다.

## 12. Phase 1에서 배운 판단 방식

Phase 1의 공통 흐름은 다음과 같다.

1. 실제 코드 경로와 데이터 규모를 확인한다.
2. mock·fixture·Testcontainers·k6·EXPLAIN 중 문제에 맞는 검증 수단을 고른다.
3. app latency, DB query, 외부 대기, thread·pool 압박을 분리한다.
4. 가장 작은 코드·schema·정책 변경으로 재현된 문제를 해결한다.
5. 개선 전후 수치나 회귀 테스트로 결과를 고정한다.
6. 측정하지 않은 운영 규모와 품질은 주장하지 않는다.
7. 더 큰 기술은 현재 해법으로 해결할 수 없는 신호가 생길 때 ADR로 비교한다.

이 때문에 Kafka, Vector DB, 분산 락, circuit breaker를 사용하지 않은 것도 결과다. “기술을 몰라서 제외했다”가 아니라 현재 문제·규모·복구 요구에 비해 과한지 먼저 판단하고 도입 조건을 문서화했다.

## 13. 다음 학습 단계

Phase 2에서는 다음 순서를 따른다.

1. Perspectives 라벨 표본을 확대한다.
2. 결과 없음과 잘못된 상위 결과를 collection, retrieval, ranking으로 분류한다.
3. 동일한 고정 데이터와 metric으로 FULLTEXT 개선 가능 범위를 먼저 확인한다.
4. 표현 차이 때문에 관련 기사가 반복 누락될 때 embedding·Vector DB를 비교한다.
5. 수집 backlog·replay·독립 consumer 요구가 생길 때 Kafka를 비교한다.
6. 고정된 CPU·memory 환경에서 단일 instance capacity를 확인한 뒤에만 scale-out 효율을 검증한다.

Phase 2도 기술 이름에서 시작하지 않고 Phase 1에서 남긴 미해결 신호에서 시작한다.
