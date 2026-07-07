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
→ AI Reviewer comment
→ Blocking 확인
→ merge
→ WORK_PROGRESS.md 갱신
```

바로 구현하지 말고, 먼저 develop 최신화, 열린 Issue/PR 확인, `WORK_PROGRESS.md`와 `BACKLOG.md` 조사를 수행한다.
새 Issue/PR 본문은 repository template을 읽고 반드시 `--body-file` 방식으로 작성한다.

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

- Repo: `SKU-GlobalTimes/GlobalTimes_BeSide`
- Base branch: `develop`
- Long-running open issue: #110 `[Troubleshooting] 서비스 설계의 근본적 한계`
- #110은 사용자가 별도로 지시하기 전까지 구현하거나 정리하지 않는다.
- #158/#159에서 다음 세션 handoff 문서 정리를 완료했다.
- #160/#161에서 Perspectives FULLTEXT relevance-first/hybrid ordering 비교를 완료했다.
- #162/#163에서 새 이슈 후보마다 overengineering 여부를 먼저 판단하는 guardrail을 추가했다.
- #164에서 hybrid ranking query variant를 바로 구현하지 않는 이유와 후속 적용 기준을 docs/ADR로 정리하는 작업을 진행 중이다.

## Recent Completed Work

- #148/#149: `KeywordExtractor` 현행 정책을 회귀 테스트로 고정하고, 현재 정책이 의미 유사도 검색이 아니라 제목 토큰 기반 후보 탐색임을 명확히 했다.
- #150/#151: MySQL FULLTEXT BOOLEAN MODE 검색어 포맷을 `+first +second third` 형태의 단일 공백 조립으로 정규화했다.
- #152/#153: `First`, `round`, `Entre` 같은 샘플 기반 일반 토큰을 필터링해 weak keyword 후보를 줄였다.
- #154/#155: RSS/News API 수집 편차와 source coverage 한계가 FULLTEXT/향후 연관도 측정 해석에 주는 영향을 별도 LOG로 남겼다.
- #156/#157: #152 전후 DB-level FULLTEXT 결과 변화를 측정했다.
- #158/#159: 긴 `WORK_PROGRESS.md`를 보완하기 위한 다음 세션 handoff 문서를 추가했다.
- #160/#161: Perspectives FULLTEXT ordering을 latest-first, relevance-first, hybrid 후보로 비교했다.
- #162/#163: 새 후보마다 overengineering 여부를 먼저 판단하는 guardrail을 추가했다.

## Why #160 Matters

#156에서 대표 샘플 8146은 키워드가 `+First +round Iran talks`에서 `+Iran +talks ends encouraging`로 바뀌며 상위 결과가 스포츠/라운드 노이즈에서 Iran/US talks 중심으로 이동했다.
다만 현재 쿼리는 여전히 `ORDER BY published_at DESC` 최신순이라 Lebanon/ceasefire 같은 인접 노이즈가 남는다.

#160에서는 동일 후보군에서 latest-first, relevance-first, hybrid 후보를 비교했다.
pure relevance-first는 wrong-context 기사를 끌어올릴 수 있어 바로 적용하기 위험하고, hybrid는 폐기하지 않되 후속 실험 기준이 필요한 후보로 남겼다.

## Current Backend Issue

현재 진행 중:

```text
#164 [DOCS/ADR] Perspectives ranking policy 도입 보류와 hybrid 후보 적용 기준 정리
```

목표:

- #160 측정 결과를 바탕으로 pure relevance-first와 hybrid ranking의 장단점을 정리한다.
- 현재 단계에서 query variant 구현이 과한지 판단하고, 바로 운영 응답을 바꾸지 않는 이유를 남긴다.
- 나중에 코드 실험을 한다면 필요한 조건을 정의한다.
- source coverage 한계와 ranking 개선 효과를 계속 분리해서 해석한다.
- 코드, API 응답, DB schema, Redis 정책, FULLTEXT query는 변경하지 않는다.

판단 기준:

- 지금 구현하면 복잡도 대비 설명 가능한 효과가 있는가?
- API 응답 변경 없이 문서/ADR로 보류 판단을 남기는 편이 더 적절한가?
- 나중에 hybrid ranking을 적용하려면 어떤 샘플, 테스트, API 기준선이 필요한가?
- source coverage 한계를 ranking 문제로 오판하지 않는가?

참고 문서:

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
- 새 작업을 시작하거나 merge할 때 이 문서의 `Current Snapshot`과 `Recommended Next Backend Issue`를 갱신한다.
- 의사결정 근거를 압축하되, 근거 문서 링크는 반드시 남긴다.

## Reviewer Agent Prompt Template

```text
너는 Reviewer Agent다.
코드 수정은 하지 말고 PR diff만 검토해줘.

Repo: SKU-GlobalTimes/GlobalTimes_BeSide
PR: <PR URL>
Issue: #164

검토 관점:
1. 이번 PR이 #164 문서/ADR 범위에 머무르고 코드, API 응답, DB schema, Redis 정책, FULLTEXT query를 변경하지 않는가?
2. ADR이 #160 측정 결과를 근거로 pure relevance-first 직접 적용을 보류하는 이유를 명확히 설명하는가?
3. hybrid ranking을 폐기하지 않고 후속 실험 조건으로 남기는 기준이 충분한가?
4. source coverage 한계와 ranking 개선 효과를 분리해서 해석하고 있는가?
5. NEXT_AGENT_BRIEF.md, WORK_PROGRESS.md, BACKLOG.md가 Issue 목적과 진행 상태를 정확히 반영하는가?
6. 민감 정보가 포함되어 있지 않은가?
7. 새 Blocking이 있는가?

제약:
- 코드 직접 수정 금지
- 커밋, push, merge 금지
- 리뷰 결과는 ## AI Reviewer 검토 결과 제목으로 GitHub PR comment에 직접 남겨줘.
```
