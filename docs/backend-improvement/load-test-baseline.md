# 주요 API 부하 테스트 기준선

## 목적

주요 API의 성능을 “빠르다/느리다”가 아니라 평균 응답 시간, p95 응답 시간, 오류율로 기록한다.
이번 작업은 개선 전 기준선을 수립하기 위한 단계이며, 실제 캐시 전략 변경이나 쿼리 최적화는 후속 이슈에서 진행한다.

## 과다 트래픽 대응과의 관계

부하 테스트는 과다 트래픽 자체를 해결하는 기능이 아니라, 과다 트래픽 상황에서 현재 병목과 한계를 확인하는 기준선이다.
이 기준선이 있어야 Redis 캐싱, 쿼리 개선, 외부 API 호출 최소화, rate limit, 비동기 처리 같은 대응책의 효과를 개선 전후 수치로 비교할 수 있다.

## 테스트 도구

k6를 사용한다.

선택 이유:

- JavaScript 기반으로 시나리오 작성이 단순하다.
- 평균, p95, 실패율 등 성능 지표를 기본 제공한다.
- CLI 실행 결과를 PR과 문서에 붙여 기록하기 쉽다.
- 이후 CI 또는 GitHub Actions와 연결하기 쉽다.

## 대상 API

| API | 목적 | 주요 지표 |
| --- | --- | --- |
| `GET /api/articles/latest` | 기본 기사 목록 조회 기준선 | 평균, p95, 오류율 |
| `GET /api/articles/cursor` | cursor 기반 목록 조회 기준선 | 평균, p95, 오류율 |
| `GET /api/articles/popular` | 조회수 기반 목록 조회 기준선 | 평균, p95, 오류율 |
| `GET /api/articles/explore` | 필터 기반 탐색 조회 기준선 | 평균, p95, 오류율 |
| `GET /api/search` | 번역 캐시와 FULLTEXT 검색 경로 기준선 | 평균, p95, 오류율 |
| `GET /api/news/{id}/perspectives` | Redis 캐시, 번역, FULLTEXT 검색이 포함된 핵심 경로 기준선 | 평균, p95, 오류율 |

## 실행 방법

로컬 서버를 먼저 실행한다.

```powershell
.\gradlew.bat bootRun
```

MySQL과 Redis가 필요한 경우 개발용 Docker Compose를 사용한다.

```powershell
docker compose -f docker-compose.dev.yml up -d
```

k6 설치 후 아래 명령으로 실행한다.

```powershell
k6 run .\load-tests\k6\api-baseline.js
```

환경 변수를 통해 대상 서버와 테스트 값을 바꿀 수 있다.

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:ARTICLE_ID = "1"
$env:SEARCH_TEXT = "대구"
k6 run .\load-tests\k6\api-baseline.js
```

## 시나리오

| 시나리오 | 기본 VU | 기본 시간 | 설명 |
| --- | --- | --- | --- |
| `articles_baseline` | 5 | 1m | 기사 목록, cursor, popular, explore API 반복 호출 |
| `search_baseline` | 3 | 1m | 동일 검색어 반복 호출로 번역 캐시와 FULLTEXT 검색 경로 측정 |
| `perspectives_repeated` | 3 | 1m | 동일 기사 perspectives 반복 호출로 캐시 적용 전후 차이를 관측 |

VU와 실행 시간은 환경 변수로 조정한다.

```powershell
$env:ARTICLES_VUS = "10"
$env:SEARCH_VUS = "5"
$env:PERSPECTIVES_VUS = "5"
$env:ARTICLES_DURATION = "3m"
$env:SEARCH_DURATION = "3m"
$env:PERSPECTIVES_DURATION = "3m"
k6 run .\load-tests\k6\api-baseline.js
```

## 결과 기록 템플릿

| 측정일 | 환경 | API/시나리오 | 평균 응답 시간 | p95 응답 시간 | 오류율 | 비고 |
| --- | --- | --- | --- | --- | --- | --- |
|  | local/dev | articles_baseline |  |  |  |  |
|  | local/dev | search_baseline |  |  |  |  |
|  | local/dev | perspectives_repeated |  |  |  |  |

## Smoke test 결과

2026-07-01 로컬 개발 환경에서 k6 실행 가능 여부를 확인하기 위한 짧은 smoke test를 수행했다.

조건:

- `BASE_URL=http://localhost:8080`
- `ARTICLE_ID=8449`
- `SEARCH_TEXT=대구`
- 각 시나리오 VU 1명
- 각 시나리오 duration 15초

결과:

| API/시나리오 | 평균 응답 시간 | p95 응답 시간 | 오류율 | 비고 |
| --- | --- | --- | --- | --- |
| 전체 | 28.38ms | 49.29ms | 0.00% | 86 requests |
| articles | 28.9ms | 48.84ms | 0.00% | latest, cursor, popular, explore |
| search | 41.77ms | 61.1ms | 0.00% | `SEARCH_TEXT=대구` |
| perspectives | 13.05ms | 15.86ms | 0.00% | 반복 호출 기준 |

추가 확인:

- `SEARCH_TEXT=war`, `korea`, `economy`, `technology`는 현재 로컬 환경에서 `/api/search` 500 응답을 반환했다.
- 해당 문제는 부하 테스트 스크립트 문제가 아니라 검색 API의 입력/데이터/쿼리 처리 이슈 후보로 보고 후속 이슈에서 분리한다.
- 위 smoke test 결과는 부하 한계 측정이 아니라 스크립트 실행 가능성과 기본 지표 수집 가능성을 확인한 결과다.

## 개선 후보 기록 템플릿

| 병목 후보 | 근거 지표 | 개선 후보 | 후속 이슈 |
| --- | --- | --- | --- |
| Perspectives cache miss | p95 증가, `cacheHit=false` 로그 | Redis 캐시 TTL/key 정책 점검 |  |
| Translation external call | `externalCall=true`, `externalCallMs` 증가 | 번역 캐시 선저장 또는 fallback 정책 개선 |  |
| FULLTEXT search | `dbSearchMs` 증가 | 검색어 정규화, 인덱스 점검 |  |
| 과다 트래픽 | 오류율 증가, p95 급증 | rate limit, 캐시, 비동기 처리 검토 |  |

## 주의사항

- 부하 테스트는 운영 환경이 아닌 로컬 또는 개발 환경에서 먼저 수행한다.
- 외부 번역 API 호출이 발생할 수 있으므로 API quota와 비용을 확인한다.
- 실제 최적화 결과는 동일한 시나리오와 유사한 데이터 조건에서 다시 측정한다.
- 수치 비교 시 평균만 사용하지 않고 p95와 오류율을 함께 기록한다.
