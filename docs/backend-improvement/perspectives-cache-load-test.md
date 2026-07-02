# Perspectives API cold/warm cache 부하 테스트

## 목적

`GET /api/news/{id}/perspectives`는 Redis 캐시, 번역, FULLTEXT 검색이 함께 포함된 핵심 경로다.
기존 `api-baseline.js`의 `perspectives_repeated` 시나리오는 첫 요청의 cache miss와 반복 요청의 cache hit를 하나의 평균/p95에 섞어 기록한다.

이 문서는 cold cache와 warm cache를 분리 측정해 Redis 캐시가 반복 조회 응답 시간을 얼마나 줄이는지 수치로 확인하기 위한 기준이다.

## 측정 시나리오

| 시나리오 | 의미 | 기대 로그 |
| --- | --- | --- |
| cold cache | Redis에 `perspectives:article:{id}` key가 없는 상태에서 첫 요청 | `cacheHit=false` |
| warm cache | cold 요청 이후 같은 articleId를 반복 조회 | `cacheHit=true` |

## k6 스크립트

```text
load-tests/k6/perspectives-cache.js
```

기본 동작:

- `cold_cache`: `ARTICLE_IDS`에 지정한 기사 ID를 1회씩 호출한다.
- `warm_cache`: `WARM_ARTICLE_ID`를 반복 호출한다.
- `warm_cache`는 기본적으로 5초 뒤 시작해, cold 요청이 먼저 캐시를 채우도록 한다.
- Redis key가 이미 존재하면 cold 측정이 warm에 가깝게 오염될 수 있다.
- cold 측정은 articleId별 1회 호출이 원칙이다.
  `COLD_ITERATIONS`가 `ARTICLE_IDS` 개수보다 크면 같은 articleId가 cold 시나리오 안에서 반복 호출되어 2번째 이후 요청은 warm cache에 가까워질 수 있다.
  반복 측정이 필요하면 여러 articleId를 지정하거나, 실행 사이마다 대상 Redis key를 삭제한다.

## 실행 전 조건

로컬 서버와 Redis/MySQL을 실행한다.

```powershell
docker compose -f docker-compose.dev.yml up -d
.\gradlew.bat bootRun
```

정확한 cold cache 측정을 위해 실행 전에 대상 key를 삭제하거나 TTL 만료 후 실행한다.

```text
perspectives:article:{ARTICLE_ID}
```

여러 articleId를 cold cache로 측정할 때는 각 articleId의 Redis key가 모두 없는 상태여야 한다.
예를 들어 `ARTICLE_IDS=8449,8450,8451`로 실행한다면 아래 key들이 모두 삭제되어 있어야 한다.

```text
perspectives:article:8449
perspectives:article:8450
perspectives:article:8451
```

`ARTICLE_ID`는 현재 DB에 존재하는 기사여야 한다.
로컬에서는 `GET /api/articles/latest?page=0&size=5` 응답의 `data.content[].id` 중 하나를 사용하면 된다.

예시:

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:ARTICLE_IDS = "8449"
$env:WARM_ARTICLE_ID = "8449"
$env:COLD_VUS = "1"
$env:COLD_ITERATIONS = "1"
$env:WARM_VUS = "1"
$env:WARM_DURATION = "15s"
& "C:\Program Files\k6\k6.exe" run .\load-tests\k6\perspectives-cache.js
```

## 결과 기록 템플릿

| 측정일 | 환경 | articleId | 시나리오 | 평균 응답 시간 | p95 응답 시간 | 오류율 | 서버 로그 |
| --- | --- | --- | --- | --- | --- | --- | --- |
|  | local/dev |  | cold cache |  |  |  | `cacheHit=false` |
|  | local/dev |  | warm cache |  |  |  | `cacheHit=true` |

## Smoke test 결과

2026-07-02 로컬 개발 환경에서 cold/warm 분리 측정 스크립트 실행 가능 여부를 확인했다.

조건:

- `BASE_URL=http://localhost:8080`
- `ARTICLE_IDS=8449`
- `WARM_ARTICLE_ID=8449`
- `COLD_VUS=1`
- `COLD_ITERATIONS=1`
- `WARM_VUS=1`
- `WARM_DURATION=15s`
- 실행 전 Redis key `perspectives:article:8449` 삭제 확인

결과:

| API/시나리오 | 평균 응답 시간 | p95 응답 시간 | 오류율 | 비고 |
| --- | --- | --- | --- | --- |
| 전체 | 30.84ms | 71.49ms | 0.00% | 16 requests |
| cold cache | 218.65ms | 218.65ms | 0.00% | 1 request |
| warm cache | 18.32ms | 21.29ms | 0.00% | 15 requests |

해석:

- 동일 articleId 기준 warm cache p95가 cold cache p95보다 크게 낮았다.
- 이 결과는 Redis cache hit가 반복 조회 응답 시간을 줄인다는 초기 근거다.
- cold cache는 1회 샘플이므로 통계적으로 충분한 부하 측정은 아니며, 여러 articleId를 대상으로 반복 측정하면 더 안정적인 비교가 가능하다.

## 해석 기준

- warm cache p95가 cold cache p95보다 충분히 낮으면 Redis 캐시가 반복 조회에 의미 있는 효과를 낸다고 볼 수 있다.
- cold cache p95가 높다면 병목 후보는 기준 기사 조회, 키워드 추출, 번역, FULLTEXT 검색, 그룹핑 중 하나다.
- warm cache p95가 높다면 Redis 조회, JSON 역직렬화, 네트워크, 응답 크기를 추가로 확인한다.
- 캐시 key, TTL, 정합성 정책 변경은 측정 결과를 근거로 후속 이슈에서 다룬다.
- 현재 Redis key, TTL, 실패 처리, stale cache 허용 판단은 `docs/backend-improvement/perspectives-redis-cache-policy.md`에 기록한다.

## 주의사항

- 현재 Perspectives API의 관련 기사 탐색은 제목 기반 키워드 추출과 MySQL FULLTEXT 검색에 의존한다.
- 이는 semantic similarity가 아니므로, 같은 사건이라도 표현이 다르면 누락될 수 있다.
- 이번 작업은 검색 품질 개선이 아니라 캐시 hit/miss 성능 차이를 분리 측정하는 작업이다.
