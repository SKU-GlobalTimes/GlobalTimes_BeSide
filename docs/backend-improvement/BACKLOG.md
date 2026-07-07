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

## 상태 표기

- `Backlog`: 아직 시작하지 않은 후보
- `Planned`: Issue 및 작업 계획이 준비된 상태
- `In Progress`: 승인된 브랜치에서 작업 중
- `Verified`: 테스트 또는 측정 결과까지 확인됨
- `Done`: PR 병합 완료

## P0. AI 작업 운영 규칙 및 개선 Backlog 정립

- 상태: `In Progress` ([#113](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/113))
- AS-IS: 개선 작업의 계획, 승인, AI 검토 결과가 GitHub 흐름과 일관되게 연결되어 있지 않다.
- TO-BE: 작업 계획과 승인 기준을 Issue, 브랜치, PR, 문서에 연결해 재현 가능한 작업 흐름을 만든다.
- 성공 기준:
  - 최소 1개의 후속 개선 작업이 문서화된 흐름을 따라 Issue부터 PR까지 진행된다.
  - 계획, 승인 범위, 검증 결과를 GitHub에서 확인할 수 있다.

## P1. Perspectives API 관측성 및 성능 기준선 확보

- 상태: `In Progress` ([#121](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/121))
- AS-IS: 캐시 미스 시 기사 조회, 키워드 추출, 번역 API 호출, FULLTEXT 검색이 요청 경로에서 수행되지만 단계별 지연 시간과 캐시 효과를 수치로 설명할 수 없다.
- TO-BE: 캐시 히트율, 단계별 처리 시간, 외부 번역 API 호출량, p95 응답 시간을 측정하고 부하 테스트 기준선을 만든다.
- 성공 기준:
  - 캐시 히트/미스 시나리오별 p50, p95, 오류율을 기록한다.
  - 번역 API 호출 횟수와 캐시 히트율을 확인할 수 있다.
  - 측정 결과를 후속 개선 PR에 비교 기준으로 남긴다.

## P2. 기사 원문 크롤링 안정성 개선

- 상태: `Backlog`
- AS-IS: 기사 상세의 요약·질의응답 요청 중 외부 언론사 페이지를 동기 크롤링하며, 외부 사이트의 지연과 실패가 사용자 요청에 직접 영향을 준다.
- TO-BE: timeout, 실패 분류, 재시도 정책, 크롤링 결과 저장 정책을 정의하고 장애 상황에서도 예측 가능한 응답을 제공한다.
- 성공 기준:
  - 연결 지연, 읽기 지연, 본문 없음, 차단 응답의 처리 규칙이 문서화되고 검증된다.
  - 동일 기사 요청 시 불필요한 재크롤링을 줄이는 기준을 확인한다.

## P3. 다국어 이슈 매칭 품질 개선

- 상태: `Backlog` (최근 완료: [#142](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/142), [#146](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/146), [#148](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/148), [#150](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/150), [#152](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/152), [#154](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/154), [#156](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/156), [#160](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/160))
- AS-IS: 기사 제목의 키워드와 영어 번역 키워드를 MySQL FULLTEXT 검색에 사용하므로, 표현이 다른 동일 이슈 또는 비영어권 기사 간 매칭이 누락될 수 있다.
- TO-BE: 기존 후보 검색을 유지하면서 이슈 유사도와 국가 다양성 기준으로 결과를 재정렬하는 정책을 검증한다.
- 최근 측정: #160에서 `ORDER BY published_at DESC` latest-first, FULLTEXT score 기반 relevance-first, bounded recency signal을 더한 hybrid 후보를 비교했다. 측정상 pure relevance-first는 wrong-context 기사도 끌어올릴 수 있어, 후속 코드 변경은 hybrid 후보 중심으로 검토하는 것이 안전하다.
- 다음 판단: hybrid ranking query variant를 바로 구현하기보다, 현재 단계에서 과한 구현인지 검토하고 도입 보류/적용 기준을 docs/ADR로 정리하는 것이 우선이다.
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
