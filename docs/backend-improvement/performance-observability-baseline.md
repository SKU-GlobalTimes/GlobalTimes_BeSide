# 주요 API 성능 관측 기준

## 목적

주요 API의 응답 지연 원인을 감으로 추정하지 않고, 로그를 통해 요청 경로의 병목 후보를 비교할 수 있게 만든다.
이번 기준선은 최적화 자체가 아니라 이후 캐시, 쿼리, 외부 API 호출 개선 전후를 비교하기 위한 관측 기반이다.

## 관측 대상 API

| API | 관측 이유 | 주요 관측 항목 |
| --- | --- | --- |
| `GET /api/news/{id}/perspectives` | 다국가 시선 제공의 핵심 경로이며 Redis 캐시, 번역, FULLTEXT 검색이 함께 수행된다. | 캐시 hit/miss, 번역 요청 여부, 검색 시간, 국가 수, 기사 수 |
| `GET /api/search` | 검색어 번역 캐시와 FULLTEXT 검색 성능을 함께 확인할 수 있다. | 번역 캐시 hit/miss, 외부 번역 호출 여부, DB 검색 시간, 결과 수 |
| `GET /api/articles/explore` | 탐색 필터와 cursor 기반 페이징의 DB 조회 비용을 확인할 수 있다. | 필터 조건, cursor 여부, DB 조회 시간, 결과 수 |
| `GET /api/articles/latest` | 기본 기사 목록 API의 페이지 기반 조회 비용을 확인할 수 있다. | page, size, DB 조회 시간, 결과 수 |
| `GET /api/articles/cursor` | cursor 기반 목록 조회의 성능 기준을 확인할 수 있다. | cursor 여부, DB 조회 시간, 결과 수, hasNext |
| `GET /api/articles/popular` | 조회수 기반 정렬과 최근 기간 필터의 조회 비용을 확인할 수 있다. | page, size, 조회 기준일, DB 조회 시간, 결과 수 |

## 로그 필드 기준

### 공통

- `totalMs`: 서비스 메서드 전체 수행 시간
- `dbQueryMs`, `dbSearchMs`: Repository 조회 시간
- `resultCount`: 응답에 포함된 결과 수

### 번역

- `cacheHit`: Redis 번역 캐시 hit 여부
- `externalCall`: 외부 번역 API 호출 여부
- `fallback`: 번역 실패 시 원문 사용 여부
- `textLength`, `translatedLength`: 민감한 원문 대신 길이만 기록

### Perspectives

- `cacheHit`: Perspectives 응답 캐시 hit 여부
- `baseLookupMs`: 기준 기사 조회 시간
- `keywordMs`: 키워드 추출 시간
- `translationRequested`: 번역 시도 여부
- `translationFallback`: 번역 실패 후 원문 키워드 사용 여부
- `translatedSearchMs`: 번역 키워드 기반 FULLTEXT 검색 시간
- `originalSearchMs`: 원문 키워드 기반 추가 FULLTEXT 검색 시간
- `countriesFound`: 관련 기사가 발견된 국가 수
- `totalArticles`: 관련 기사 수

## 기록 원칙

- 검색어와 번역 결과 원문은 로그에 남기지 않고 길이만 기록한다.
- API 키, 인증 정보, 외부 API 응답 전문은 기록하지 않는다.
- 이번 변경은 관측 기준 추가이며 캐시 TTL, DB 인덱스, 쿼리 전략은 변경하지 않는다.
- p50, p95, 오류율 등 부하 테스트 지표는 별도 작업에서 동일 로그 기준으로 수집한다.
