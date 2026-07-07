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

## Current Snapshot

- Repo: `SKU-GlobalTimes/GlobalTimes_BeSide`
- Base branch: `develop`
- Long-running open issue: #110 `[Troubleshooting] 서비스 설계의 근본적 한계`
- #110은 사용자가 별도로 지시하기 전까지 구현하거나 정리하지 않는다.
- 최근 열린 PR은 없는 상태에서 #158 문서화 작업을 시작했다.

## Recent Completed Work

- #148/#149: `KeywordExtractor` 현행 정책을 회귀 테스트로 고정하고, 현재 정책이 의미 유사도 검색이 아니라 제목 토큰 기반 후보 탐색임을 명확히 했다.
- #150/#151: MySQL FULLTEXT BOOLEAN MODE 검색어 포맷을 `+first +second third` 형태의 단일 공백 조립으로 정규화했다.
- #152/#153: `First`, `round`, `Entre` 같은 샘플 기반 일반 토큰을 필터링해 weak keyword 후보를 줄였다.
- #154/#155: RSS/News API 수집 편차와 source coverage 한계가 FULLTEXT/향후 연관도 측정 해석에 주는 영향을 별도 LOG로 남겼다.
- #156/#157: #152 전후 DB-level FULLTEXT 결과 변화를 측정했다.

## Why #156 Matters

#156에서 대표 샘플 8146은 키워드가 `+First +round Iran talks`에서 `+Iran +talks ends encouraging`로 바뀌며 상위 결과가 스포츠/라운드 노이즈에서 Iran/US talks 중심으로 이동했다.
다만 현재 쿼리는 여전히 `ORDER BY published_at DESC` 최신순이라 Lebanon/ceasefire 같은 인접 노이즈가 남는다.

이 때문에 다음 P3 후보는 keyword extractor를 더 넓게 만지는 것보다, 동일 후보군에서 정렬 기준을 비교하는 작업이 자연스럽다.

## Recommended Next Backend Issue

추천 후보:

```text
[PERF] Perspectives FULLTEXT relevance-first/hybrid ordering 비교
```

목표:

- 현재 latest-first 정렬과 `MATCH(title, description) AGAINST(...)` score 기반 relevance-first 정렬을 비교한다.
- 필요하면 relevance score와 `published_at`을 함께 쓰는 hybrid ordering 후보를 비교한다.
- 8146, 9440, 8147, 8149 같은 기존 대표 샘플을 우선 사용한다.
- API 동작을 바로 변경하기보다 DB-level 측정 문서부터 만든다.

판단 기준:

- 관련성 높은 기사들이 상위에 더 안정적으로 올라오는가?
- 국가/언어 다양성이 과하게 무너지지 않는가?
- 최신성 요구와 relevance 요구의 trade-off가 설명 가능한가?
- source coverage 한계 때문에 생기는 누락을 ranking 문제로 오판하지 않는가?

참고 문서:

- `docs/backend-improvement/perspectives-fulltext-generic-token-filter-result.md`
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
Issue: #<ISSUE NUMBER>

검토 관점:
1. 이번 PR이 문서/handoff 구조 정리 범위에 머무르는가?
2. NEXT_AGENT_BRIEF.md가 새 세션이 현재 workflow, 최근 완료 작업, #110 제외 조건, 다음 추천 후보를 빠르게 파악하기에 충분한가?
3. WORK_PROGRESS.md와 BACKLOG.md가 Issue 목적과 진행 상태를 정확히 반영하는가?
4. #156 이후 다음 후보로 FULLTEXT relevance-first/hybrid ordering 비교를 제안하는 근거가 기존 측정 문서와 일치하는가?
5. 민감 정보가 포함되어 있지 않은가?
6. 새 Blocking이 있는가?

제약:
- 코드 직접 수정 금지
- 커밋, push, merge 금지
- 리뷰 결과는 ## AI Reviewer 검토 결과 제목으로 GitHub PR comment에 직접 남겨줘.
```
