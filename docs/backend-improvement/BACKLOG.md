# 백엔드 개선 Backlog

## 목적

GlobalTimes 백엔드의 개선 작업을 문제 정의부터 검증 결과까지 추적 가능하게 관리한다.
각 항목은 GitHub Issue, 작업 브랜치, PR, 테스트 및 측정 결과로 연결한다.

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

- 상태: `In Progress` ([#178](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/178))
- 완료 근거: [#121](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/121), [#174](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/174), [#176](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/176)
- AS-IS: 캐시 미스 시 기사 조회, 키워드 추출, 번역 API 호출, FULLTEXT 검색이 요청 경로에서 수행되지만 단계별 지연 시간과 캐시 효과를 수치로 설명할 수 없다.
- TO-BE: 캐시 히트율, 단계별 처리 시간, 외부 번역 API 호출량, p95 응답 시간을 측정하고 부하 테스트 기준선을 만든다.
- 성공 기준:
  - 캐시 히트/미스 시나리오별 p50, p95, 오류율을 기록한다.
  - 번역 API 호출 횟수와 캐시 히트율을 확인할 수 있다.
  - 측정 결과를 후속 개선 PR에 비교 기준으로 남긴다.
  - #174에서 외부 호출 없는 주요 기사 조회 API의 고부하 p95/오류율과 DB 병목 후보를 분리 측정했다.
  - #176에서 `popular` 조회의 `Using filesort`를 k6와 `EXPLAIN ANALYZE`로 확인하고, 현재 규모에서는 인덱스 추가를 보류하는 판단 기준을 남긴다.
  - #178에서 로컬 부하 테스트 시 k6 결과, 애플리케이션 latency 로그, Docker 리소스 지표를 같은 실행 구간에 맞춰 해석하는 runbook을 정리한다.

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

- 상태: `Backlog`
- AS-IS: 기사 상세의 AI 요약/질의응답 경로에는 Gemini 외부 호출이 포함될 수 있지만, 실제 Gemini API로 부하 테스트를 반복하면 비용, quota, rate limit, 응답 편차 문제가 생긴다.
- TO-BE: mock AI 서버로 latency `200ms`, `1s`, `3s`, `timeout/5xx` 조건을 통제하고, 사용자 요청 경로의 p95/오류율/fallback 동작을 측정한다.
- 진행 조건:
  - #178의 로컬 부하 테스트 로그/리소스 캡처 기준을 먼저 적용한다.
  - mock 서버는 테스트/로컬 설정에만 사용하고 운영 API key, prompt 전문, 사용자 질문 전문은 문서/로그에 남기지 않는다.
- 예상 이슈:
  - `[PERF] AI 질의응답 Gemini 외부 호출 mock latency 부하 테스트`

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
