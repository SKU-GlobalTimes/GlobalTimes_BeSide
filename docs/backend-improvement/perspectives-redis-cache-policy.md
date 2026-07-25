# Perspectives Redis 캐시 정책

## 목적

`GET /api/news/{id}/perspectives`는 키워드 추출, 선택적 번역, MySQL FULLTEXT 검색 후 관련 기사를 국가별로 묶는다.
이 경로는 단순 기사 조회보다 비용이 크므로 `PerspectivesService`가 계산 결과를 Redis에 저장한다.

이 문서는 이후 성능 작업에서 의도적으로 정책을 변경할 수 있도록 현재 캐시 정책, 실패 동작, stale data의 trade-off를 기록한다.

## 용어

| 용어 | 이 프로젝트에서의 의미 |
| --- | --- |
| Cache deletion | `perspectives:article:8449` 삭제처럼 Redis key를 직접 제거한다. 테스트나 수동 정리에 유용하다. |
| Cache invalidation | 원본 데이터가 변경돼 캐시가 오래된 값이 될 때 자동으로 제거하거나 갱신한다. |
| Stale cache | 캐시 저장 후 DB 데이터가 바뀌었지만 계속 제공되는 캐시 응답이다. |
| Stale allowance | 조회가 많고 핵심 정합성 데이터가 아닐 때, 보통 TTL로 제한된 시간 동안 오래된 값을 허용하는 의도적인 결정이다. |

## 현재 정책

| 항목 | 현재 동작 |
| --- | --- |
| Key prefix | `perspectives:article:` |
| Key 형태 | `perspectives:article:{articleId}` |
| Value | 직렬화한 `PerspectivesResDTO` JSON |
| TTL property | `perspectives.cache-ttl-seconds` |
| 기본 TTL | `3600` seconds |
| 캐시 비활성 조건 | `perspectives.cache-ttl-seconds=0` |
| 명시적 invalidation | 구현하지 않음 |

설정:

```yaml
perspectives:
  cache-ttl-seconds: 3600
```

## 실행 흐름

### Cache hit

1. Redis에서 `perspectives:article:{articleId}`를 읽는다.
2. JSON을 `PerspectivesResDTO`로 역직렬화한다.
3. 캐시 응답을 반환한다.
4. `cacheHit=true`, `cacheReadMs`, `totalMs`, `countriesFound`, `totalArticles`를 로그로 남긴다.

### Cache miss

1. MySQL에서 기준 기사를 조회한다.
2. 제목에서 일반 키워드와 FULLTEXT boolean 키워드를 추출한다.
3. 영어가 아닌 기사 키워드를 영어로 번역한다.
4. MySQL FULLTEXT로 관련 기사를 검색한다.
5. 결과를 국가별로 묶고 국가당 기사 수를 3개로 제한한다.
6. 응답을 직렬화해 TTL과 함께 Redis에 저장한다.
7. `cacheHit=false`와 단계별 시간을 로그로 남긴다.

## 실패 동작

| 실패 상황 | 현재 동작 | API 영향 |
| --- | --- | --- |
| Redis 읽기 실패 | 경고 후 DB/FULLTEXT에서 재계산 | 응답 성공 가능 |
| Cache JSON 역직렬화 실패 | 경고 후 DB/FULLTEXT에서 재계산 | 응답 성공 가능 |
| Redis 쓰기 실패 | 경고 후 무시 | 응답은 성공하고 다음 요청도 cold일 수 있음 |
| 기준 기사 없음 | 도메인 예외 발생 | 기존과 동일하게 응답 실패 |

Redis는 원본 데이터가 아니라 최적화 계층으로 취급한다.
원본 데이터의 기준은 MySQL이다.

## Stale cache 분석

현재 서비스는 뉴스 수집 후 조회 비중이 높다.
따라서 TTL이 제품 기대에 맞게 충분히 짧다면 Perspectives 응답의 제한된 stale cache를 허용할 수 있다.

| 데이터 변경 | Perspectives 응답에 영향? | 현재 처리 | 참고 |
| --- | --- | --- | --- |
| 새 관련 기사 삽입 | 예 | TTL 만료까지 기존 캐시 유지 | 새 관련 기사가 즉시 나타나지 않을 수 있다. |
| 기준 기사 제목 변경 | 예 | TTL 만료까지 기존 캐시 유지 | 키워드 추출 결과가 오래된 값일 수 있다. |
| 기사 국가/카테고리 변경 | 예 | TTL 만료까지 기존 캐시 유지 | 국가 그룹이 오래된 값일 수 있다. |
| 기사 출처 변경 | 가능 | TTL 만료까지 기존 캐시 유지 | 하위 기사 DTO에 출처 이름이 포함된다. |
| 조회 수 증가 | 아니요 | invalidation 불필요 | 조회 수는 Perspectives 응답에 없다. |
| 요약 갱신 | 아니요 | invalidation 불필요 | 요약은 Perspectives 응답에 없다. |
| 크롤링 본문 갱신 | 아니요 | invalidation 불필요 | 크롤링 본문은 Perspectives 응답에 없다. |
| 스크랩/채팅 데이터 변경 | 아니요 | invalidation 불필요 | Perspectives 응답에서 사용하지 않는다. |

## 결정

현재 프로젝트 단계에서는 명시적 invalidation을 추가하지 않고 TTL 기반 stale allowance를 유지한다.

근거:

- Perspectives는 조회가 많은 파생 응답이다.
- 현재 API 표면에서 Perspectives에 영향을 주는 기사 수정은 드물다.
- 뉴스 수집으로 관련 기사가 추가될 수 있지만, 이 기능에서는 최대 TTL 시간 뒤 노출되어도 허용할 수 있다.
- 명시적 invalidation은 관련 기사 매칭에 영향을 주는 모든 쓰기 경로에 캐시 삭제를 연결해야 한다.
- 현재 1시간 TTL은 측정된 warm cache 이점을 유지하면서 오래된 응답 시간을 제한한다.

## Invalidation 추가 조건

다음 중 하나가 성립하면 명시적 invalidation을 추가한다.

- 관리 API가 기사 제목, 국가, 카테고리, 출처 또는 언어를 수정하기 시작한다.
- 뉴스 수집이 잦아져 새 관련 기사를 즉시 표시해야 한다.
- 사용자가 오래된 Perspectives 결과를 정합성 문제로 보고한다.
- Cache TTL을 현재 1시간보다 크게 늘려야 한다.

invalidation 대상 후보:

```text
perspectives:article:{baseArticleId}
```

변경된 기사 key만 삭제하는 것으로 완전한 정합성을 보장하기는 어렵다.
새 기사가 기존 여러 기준 기사와 관련 있다면 해당 기준 기사 캐시도 오래된 값이 된다.
이 광범위한 invalidation 문제는 별도 설계 이슈로 다뤄야 한다.

## 후속 후보

- 여러 기사 ID로 cold cache를 측정해 FULLTEXT 편차를 파악한다.
- `ArticleRepository.findPerspectives`에 MySQL `EXPLAIN`을 수행한다.
- Warm cache hit ratio와 stale 허용 범위를 측정한 뒤 TTL을 재검토한다.
- Redis hit 경로 자체가 병목이 될 때만 local cache를 검토한다.
- 높은 트래픽에서 cold cache 지연이 사용자에게 드러날 때만 비동기 사전 계산을 검토한다.
