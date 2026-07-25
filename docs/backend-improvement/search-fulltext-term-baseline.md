# 검색 API FULLTEXT 검색어별 기준선

## 목적

이 문서는 검색어 유형별 `GET /api/search` 반복 측정 기준을 정의한다. #133/#134의 MySQL FULLTEXT 실행 계획 개선을 API p95 응답시간, 실패율, 결과 수와 연결한다.

이 작업은 코드 동작, DB schema, FULLTEXT query, API 응답, Redis 정책, 번역 정책을 변경하지 않는다.

## 핵심 결과 요약

```text
검색 API의 MySQL FULLTEXT 쿼리를 검색어 유형별로 측정하고 실행 계획 개선 근거와 연결해, p95 응답 시간과 실패율 기준선을 수립했습니다.
```

압축 표현:

```text
검색 API MySQL FULLTEXT 실행 계획 분석 및 검색어별 p95/실패율 기준선 수립
```

## 배경

#133/#134에서는 검색 API의 FULLTEXT query 경로를 분석했다. 영어 입력에서 원문과 번역문이 같으면 기존의 중복 `OR MATCH` 때문에 MySQL이 의도한 FULLTEXT index 대신 `idx_article_published_at`을 선택할 수 있었다.

현재 코드는 원문과 번역문이 같을 때 중복 `OR MATCH`를 피하고, 두 값이 다를 때만 `OR` query를 유지한다.

#133에서 확인한 로컬 DB 일치 수:

| 검색어 | 일치 수 | 해석 |
| --- | ---: | --- |
| `war` | 404 | 영어 동일 검색어, 다수 결과 표본 |
| `technology` | 82 | 영어 동일 검색어, 중간 결과 표본 |
| `economy` | 39 | 영어 동일 검색어, 적은 결과 표본 |
| `대구` OR `daegu` | 0 | 한국어를 영어로 번역했지만 현재 결과가 없는 표본 |

#123/#126과 #133/#134 이후 API 확인:

| 요청 | 상태 | 설명 |
| --- | --- | --- |
| `/api/search?text=war` | 200 | 영어, 다수 결과 |
| `/api/search?text=economy` | 200 | 영어, 비교적 적은 결과 |
| `/api/search?text=technology` | 200 | 영어, 중간 규모 결과 |
| `/api/search?text=대구` | 200 | `daegu`로 번역되지만 현재 결과는 없음 |

## 측정 도구

`load-tests/k6/search-fulltext-terms.js`를 사용한다.

script는 쉼표로 구분한 검색어를 `/api/search`에 요청하고 각 요청에 `searchTerm` tag를 붙인다. 응답의 `data.searchArticles.length`는 custom `search_result_count` metric으로 기록한다.

기본 검색어:

```text
war,korea,economy,technology,대구,AI
```

## 검색어 집합

| 유형 | 검색어 | 선정 이유 |
| --- | --- | --- |
| 영어 다수 결과 | `war` | #133에서 확인한 다수 결과 동일 검색어 FULLTEXT 경로 |
| 영어 일반 주제 | `korea` | #123의 500 수정 이후 일반 영어 검색 동작 확인 |
| 영어 적은 결과 | `economy` | 결과 수가 적은 경로 확인 |
| 영어 중간 결과 | `technology` | #133의 중간 결과 표본 |
| 한국어 번역·결과 없음 | `대구` | 번역·원문 검색 결과가 없어도 200을 반환하는지 확인 |
| 짧은 기술 용어 | `AI` | 짧은 대문자 입력과 결과 수 편차 확인 |

결과 수만으로 검색 품질을 판단하지 않는다. 이 기준선은 우선 성능과 안정성을 기록한다. 검색 관련성과 다국어 품질은 별도 표본과 수동 판정이 필요하다.

## 실행

먼저 로컬 의존성과 서버를 실행한다.

```powershell
docker compose -f docker-compose.dev.yml up -d
.\gradlew.bat bootRun
```

짧은 smoke 기준선:

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:SEARCH_TERMS = "war,korea,economy,technology,대구,AI"
$env:SEARCH_VUS = "1"
$env:SEARCH_DURATION = "30s"
$env:SLEEP_SECONDS = "1"
k6 run .\load-tests\k6\search-fulltext-terms.js
```

긴 로컬 기준선:

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:SEARCH_TERMS = "war,korea,economy,technology,대구,AI"
$env:SEARCH_VUS = "3"
$env:SEARCH_DURATION = "2m"
$env:SLEEP_SECONDS = "1"
k6 run .\load-tests\k6\search-fulltext-terms.js
```

선택 filter:

```powershell
$env:COUNTRY = "kr"
$env:CATEGORY = "business"
$env:DATE = "2026-07-01"
```

## 결과 기록 양식

k6 출력에서 p95와 실패율을 기록한다. `searchTerm`별 `search_result_count`를 기록하고 필요하면 `SearchArticlesService` log의 `resultCount`, `dbSearchMs`, `totalMs`를 확인한다.

| 날짜 | 환경 | 검색어 | 유형 | p95 | 실패율 | 결과 수 | 비고 |
| --- | --- | --- | --- | ---: | ---: | ---: | --- |
|  | local/dev | `war` | 영어 다수 결과 |  |  |  |  |
|  | local/dev | `korea` | 영어 일반 주제 |  |  |  |  |
|  | local/dev | `economy` | 영어 적은 결과 |  |  |  |  |
|  | local/dev | `technology` | 영어 중간 결과 |  |  |  |  |
|  | local/dev | `대구` | 한국어 번역·결과 없음 |  |  |  |  |
|  | local/dev | `AI` | 짧은 기술 용어 |  |  |  |  |

## 최초 로컬 smoke 결과

2026-07-09에 로컬 Docker MySQL/Redis와 `http://localhost:8080`의 `bootRun` 서버에서 측정했다. 각 검색어를 `SEARCH_VUS=1`, `SEARCH_DURATION=10s`, `SLEEP_SECONDS=1`로 따로 실행했다.

이는 capacity 한계가 아닌 smoke 기준선이다. 향후 query, cache, 검색 정책 변경을 같은 환경에서 비교할 때 사용한다.

| 날짜 | 환경 | 검색어 | 유형 | p95 | 실패율 | 결과 수 | 요청 수 | check | 비고 |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 2026-07-09 | local/dev | `war` | 영어 다수 결과 | 76.63ms | 0.00% | 100 | 10 | 100.00% | API limit 크기의 결과 |
| 2026-07-09 | local/dev | `korea` | 영어 일반 주제 | 60.38ms | 0.00% | 40 | 10 | 100.00% | 일반 영어 검색어 |
| 2026-07-09 | local/dev | `economy` | 영어 적은 결과 | 63.24ms | 0.00% | 39 | 10 | 100.00% | 적은 결과 표본 |
| 2026-07-09 | local/dev | `technology` | 영어 중간 결과 | 103.41ms | 0.00% | 82 | 10 | 100.00% | 이 smoke 실행에서 가장 느린 표본 |
| 2026-07-09 | local/dev | `대구` | 한국어 번역·결과 없음 | 50.14ms | 0.00% | 0 | 10 | 100.00% | 결과 없음도 정상 |
| 2026-07-09 | local/dev | `AI` | 짧은 기술 용어 | 24.78ms | 0.00% | 0 | 10 | 100.00% | 결과 없음도 정상 |

## 해석 규칙

- 결과 0건의 2xx 응답은 실패가 아니라 정상적인 FULLTEXT 결과일 수 있다.
- 결과가 많아도 관련성이 낮을 수 있다. 이 문서는 relevance 기준선이 아니다.
- 영어 동일 검색어가 느리면 #133의 EXPLAIN 기록과 단일 `MATCH` 경로 사용 여부를 확인한다.
- 원문과 번역문이 다르고 느리면 query rewrite 전에 `OR MATCH` 경로를 별도로 분석한다.
- MySQL FULLTEXT 조정으로 해결하기 어려운 recall 누락, 다국어 관련성 저하, p95 불안정이 반복될 때만 Elasticsearch 등 다른 검색 엔진을 검토한다.

## 현재 범위 판단

이 작업은 측정 기준선과 script를 마련했다. `node --check`, `k6 inspect`, `git diff --check`, `localhost:8080` 대상 로컬 k6 smoke 실행으로 script 형태를 검증했다. 검색어별 p95, 실패율, 결과 수, 요청 수, check 성공률을 기록했다.

다음 변경은 의도적으로 제외했다.

- repository query 변경
- Elasticsearch 추가
- 번역 동작 변경
- API 응답 변경
- 신규 DB index 추가

향후 query, cache, 검색 정책 변경 후 같은 검색어 집합을 반복해 유형별 p95와 실패율 추세를 비교한다.
