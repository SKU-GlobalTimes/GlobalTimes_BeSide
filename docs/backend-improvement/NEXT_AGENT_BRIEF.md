# Backend Improvement Next Agent Brief

이 문서는 새 Codex 세션이 `WORK_PROGRESS.md` 전체를 읽기 전에 현재 상태를 빠르게 복원하기 위한 짧은 handoff 문서다.
상세한 이력과 근거는 `WORK_PROGRESS.md`, `BACKLOG.md`, 개별 측정 문서를 기준으로 확인한다.

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
