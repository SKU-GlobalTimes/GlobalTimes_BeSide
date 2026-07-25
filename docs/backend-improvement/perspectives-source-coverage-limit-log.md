# Perspectives 수집 범위 한계 기록

## 목적

이 기록은 Perspectives 매칭 품질을 해석할 때 고려해야 하는 데이터 측 한계를 남긴다.

Perspectives 매칭은 키워드 추출, MySQL FULLTEXT, 번역, 순위 또는 향후 semantic similarity뿐 아니라 RSS와 News API에서 실제 수집된 기사 데이터에도 제한된다.

이 문서는 측정 및 ADR 해석을 돕는다.
크롤러 동작, RSS feed, DB schema, FULLTEXT query 형태, 순위 또는 캐시 정책은 변경하지 않는다.

## 배경

프로젝트는 여러 외부 출처에서 기사를 수집한다.
이 출처들은 균형 잡힌 사건 데이터셋처럼 동작하지 않는다.

- RSS feed마다 갱신 주기가 다를 수 있다.
- 각 언론사는 RSS feed에 서로 다른 기사를 선택할 수 있다.
- 국가·언어·카테고리별 수집 기사 수가 다를 수 있다.
- 같은 국제 이슈도 출처마다 게시 시점이 다를 수 있다.
- 관련 기사가 현재 수집하는 카테고리나 feed 밖에 게시될 수 있다.
- News API 무료 요금제 제한과 RSS feed 범위가 DB에 들어오는 기사에 영향을 준다.

따라서 Perspectives 품질은 검색 품질과 데이터 수집 범위 모두에 제한된다.

## 해석 규칙

FULLTEXT, keyword, vector, embedding 또는 향후 issue clustering 품질을 측정할 때 다음 두 원인을 분리한다.

| 관찰 | 검색 측 해석 | 데이터 측 해석 |
| --- | --- | --- |
| `0` matches | Query term, language, tokenization 또는 ranking이 좋지 않을 수 있다. | 관련 기사가 아직 수집 DB에 없을 수 있다. |
| 낮은 국가 다양성 | 한 언어나 출처에 매칭이 치우칠 수 있다. | 일부 국가 feed가 해당 이슈를 제공하지 않았을 수 있다. |
| 영어 외 관련 결과 없음 | 번역 또는 다국어 검색이 불충분할 수 있다. | 비영어 출처가 기사를 게시하지 않았거나 수집되지 않았을 수 있다. |
| 잡음 결과 | 키워드가 너무 넓거나 최신순 정렬에 치우칠 수 있다. | DB에 넓은 주제 기사는 많고 정확히 같은 이슈 기사는 적을 수 있다. |
| 쿼리 조정 후 겉보기 개선 | 검색어가 개선됐을 수 있다. | 해당 표본의 수집 범위가 다른 이슈보다 좋았을 뿐일 수 있다. |

## FULLTEXT 고유 한계

MySQL FULLTEXT는 로컬 `article` 테이블에 존재하는 텍스트만 검색할 수 있다.
다음 기사는 찾을 수 없다.

- 아직 수집되지 않은 기사
- feed가 해당 이슈를 노출하지 않은 기사
- 설정된 RSS/News API 목록 밖의 출처 기사
- 생성 쿼리와 단어가 충분히 겹치지 않는 동일 이슈 기사

따라서 FULLTEXT 결과 변화 측정에서 매칭 수만으로 "알고리즘이 나쁘다" 또는 "알고리즘을 해결했다"고 결론 내리면 안 된다.
반환 예시를 기록하고 검색 실패와 수집 범위 부족 가능성을 구분해야 한다.

## ADR에 미치는 영향

Elasticsearch, vector search, embeddings, RAG 또는 issue clustering ADR은 다음 경계를 사용해야 한다.

| 후보 | 개선 가능한 부분 | 단독으로 해결할 수 없는 부분 |
| --- | --- | --- |
| FULLTEXT tuning | Token/query 형태, index 사용, 단순 keyword recall | 누락 기사 또는 수집하지 않은 출처 |
| 더 나은 keyword extraction | 불필요한 제목 token과 약한 필수 검색어 | 출처 갱신 지연 또는 feed 선택 편향 |
| Elasticsearch | Search analyzer, ranking, 다국어 text 처리 | 수집된 동일 이슈 기사 자체의 부재 |
| Vector/embedding search | 같은 이슈의 서로 다른 표현 | DB에 없는 기사 |
| Issue clustering | 수집 기사의 안정적인 그룹화 | 외부 출처 범위와 최신성 |
| RAG | 검색된 콘텐츠에 대한 설명 | 원본 데이터 누락으로 인한 retrieval gap |

대표 이슈가 수집 데이터셋에 없다면 검색 계층 기술로 복원할 수 없다.
올바른 후속 작업은 검색 순위가 아니라 출처 범위 분석, 수집 일정, feed 확장 또는 데이터 최신성 추적일 수 있다.

## 측정 지침

향후 모든 Perspectives 품질 snapshot에는 다음을 기록한다.

- 측정일과 로컬 데이터셋 크기
- 가능한 경우 표본 기사 ID, 국가, 언어, 카테고리, 출처, 게시 시각
- 변경 전후 생성 keyword
- 매칭 수와 반환 국가/언어 분포
- 수동 품질 검토를 위한 일부 반환 제목 메모
- `0`건 또는 낮은 다양성이 검색 동작이 아닌 수집 범위에서 비롯됐을 가능성

매칭 수만 성공 지표로 사용하지 않는다.
결과가 다음 중 어디에 가까운지 짧게 해석한다.

- 검색/쿼리 한계
- 데이터 수집 범위 한계
- 혼합 한계
- 근거 부족

## 현재 P3 흐름과의 연결

현재 P3 작업에서 이미 다음을 만들었다.

- 매칭 품질 기준선
- 로컬 FULLTEXT 표본 snapshot
- `KeywordExtractor` 회귀 테스트
- BOOLEAN MODE 형식 정규화
- 표본 기반 일반 token 필터링

다음 DB 수준 FULLTEXT 결과 측정에서는 keyword 조정으로 충분한지 ADR이 필요한지 결정하기 전에 이 기록을 해석 guardrail로 사용해야 한다.
