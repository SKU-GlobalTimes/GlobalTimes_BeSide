# Backend Phase 1 구조·근거 맵

- Issue: [#239](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/239)
- 상태: `Done` ([PR #240](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/240))

## 1. 문서 목적

이 문서는 Phase 1에서 수행한 백엔드 개선을 현재 코드 구조와 연결해 읽기 위한 진입점이다. 작업 순서만 나열하지 않고 다음 질문에 답하도록 구성한다.

```text
요청 또는 scheduler는 어디에서 시작하는가?
→ 어떤 Service와 Repository를 거치는가?
→ MySQL, Redis, 외부 API는 어떤 역할인가?
→ 기존 문제는 무엇이었는가?
→ 어떤 작은 변경으로 해결했는가?
→ 수치와 회귀 테스트는 무엇을 증명하는가?
→ 더 자세한 근거는 어느 문서와 PR에 있는가?
```

상세 수치의 원본은 개별 측정 문서와 PR이다. 빠른 수치 비교는 [백엔드 개선 정량 결과 인덱스](backend-improvement-quantitative-index.md), 전체 작업 이력은 [WORK_PROGRESS](WORK_PROGRESS.md), 다음 세션 상태는 [NEXT_AGENT_BRIEF](NEXT_AGENT_BRIEF.md)를 기준으로 한다.

## 2. 전체 시스템을 보는 순서

GlobalTimes 백엔드는 크게 여섯 흐름으로 볼 수 있다.

| 흐름 | 진입점 | 핵심 처리 | 주요 의존성 |
| --- | --- | --- | --- |
| 기사 수집 | `NewsApiService`, `RssNewsService` scheduler | 외부 응답 파싱, 중복 제거, batch 저장 | News API, RSS, MySQL |
| Trend | `TrendScheduler`, `TrendController`, `TrendAiController` | 국가별 trend 수집·Redis 교체, 기사 crawl·요약 | Google Trends RSS, Redis, Gemini |
| 기사 조회·검색 | `ArticleController`, `SearchController`, `DetailController` | latest/popular, FULLTEXT 검색, Perspectives | MySQL FULLTEXT, Redis, Translation |
| 기사 AI | `AiController` | 저장 summary 조회, crawl, Gemini 요약·SSE 질의 | MySQL, Gemini, crawler, async executor |
| 사용자 데이터 | `ChatHistoryController`, `ScrapController`, `AuthController` | 로그인 채팅·스크랩, 익명 채팅, 인증·인가 | MySQL, Redis, JWT |
| 재현성과 검증 | Flyway, Gradle test, GitHub Actions | schema 재현, 회귀 테스트, PR CI | MySQL/Redis Testcontainers |

Phase 1의 공통 원칙은 새 기술을 먼저 넣는 것이 아니라 기존 경로에서 문제를 재현하고, 측정 또는 회귀 테스트로 원인을 좁힌 뒤 가장 작은 변경을 적용하는 것이었다.

## 3. 기사 수집과 데이터 정합성

### 3.1 실행 경로

News API 수집은 `NewsApiService`, RSS 수집은 `RssNewsService`에서 시작한다. `news-fetch.enabled=false`이면 시작 시점 수집을 건너뛴다. 활성화된 환경에서는 News API `top-headlines`가 4시간마다, 고정 domain `everything` 수집이 매일 0시에 실행되고 RSS는 6시간 주기로 실행된다.

```text
@PostConstruct / @Scheduled
→ 외부 News API 또는 언론사 RSS 호출
→ 응답 DTO·XML 파싱
→ 응답 내부 URL 중복 제거
→ DB에 이미 존재하는 URL 일괄 조회
→ 신규 Article·Source 구성
→ saveAll
→ 신규/중복/무효/실패 통계 로그
```

핵심 코드는 다음 순서로 읽는다.

1. [NewsApiService](../../src/main/java/com/example/globalTimes_be/externalApi/service/NewsApiService.java)
2. [RssNewsService](../../src/main/java/com/example/globalTimes_be/externalApi/service/RssNewsService.java)
3. [ArticleRepository](../../src/main/java/com/example/globalTimes_be/domain/article/repository/ArticleRepository.java)
4. [Article entity](../../src/main/java/com/example/globalTimes_be/domain/article/entity/Article.java)
5. [V3 URL UNIQUE migration](../../src/main/resources/db/migration/V3__enforce_article_url_uniqueness.sql)

### 3.2 해결한 문제

| 문제 | 변경 | 검증 결과 | 근거 |
| --- | --- | --- | --- |
| 수집 응답 내부 중복과 DB 기존 URL을 건별 처리 | 응답 내부 `Set`과 URL 일괄 조회 후 신규 목록만 `saveAll` | 동일 batch 재실행 추가 저장 0건 | [PR #197](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/197) |
| 애플리케이션 검사만으로 과거·동시 INSERT 중복을 최종 차단할 수 없음 | URL SHA-256 generated column과 UNIQUE, Flyway 중복 정리 | 기사 `9,853 → 9,814`, 초과 중복 `39 → 0`; 동시 동일 URL 1건만 성공 | [기사 URL 유일성](article-url-uniqueness.md), [PR #211](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/211) |
| 외부 API 500·timeout을 실제 API로 반복 재현하기 어려움 | random-port mock upstream과 MySQL Testcontainers E2E | 정상 2건 저장, 실패 category 격리, 재실행 중복 0건 | [News API 실패 E2E](news-api-ingestion-failure-e2e.md), [PR #213](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/213) |
| source별 신규·중복·무효·실패 상태가 한 숫자에 섞임 | source별 batch 통계와 freshness·coverage 기준 정의 | fixture 기반 통계 회귀, 실제 DB snapshot과 구분 | [수집 freshness·coverage](collection-freshness-coverage-baseline.md), [PR #224](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/224) |
| Kafka 검토에 필요한 현재 batch 처리량 수치가 없음 | 100·500·1,000건과 순차 upstream 지연을 MySQL에서 측정 | 1,000건 News `4,244.38ms`, RSS `2,860.14ms`; 재실행 `53.83/56.15ms`, 저장 0건 | [수집 batch 처리량](collection-batch-throughput-baseline.md), [PR #234](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/234) |

### 3.3 수치 해석

1,000건 fixture 결과는 현재 수집 코드와 MySQL schema의 통제된 처리량이다. 실제 언론사 RSS 응답시간, 운영 네트워크, 장기 DB 증가량을 포함한 운영 capacity가 아니다.

News API 1,000건의 SQL statement 수가 1,022이고 RSS가 1,003이라는 결과는 batch 저장을 사용해도 JPA entity 관계와 저장 과정에서 SQL이 여전히 발생함을 보여준다. 그러나 4/6시간 scheduler 주기와 비교해 다음 실행과 겹칠 근거는 확인되지 않았다. 따라서 Phase 1에서는 Kafka나 durable queue를 도입하지 않았다.

### 3.4 이어서 읽을 문서

1. [수집 중복·실패 E2E](news-api-ingestion-failure-e2e.md)
2. [기사 URL DB 유일성](article-url-uniqueness.md)
3. [수집 freshness·coverage](collection-freshness-coverage-baseline.md)
4. [수집 batch 처리량](collection-batch-throughput-baseline.md)
5. [Flyway schema 기준선](flyway-schema-baseline.md)

## 4. Trend 수집·조회·AI 요약

### 4.1 실행 경로

```text
TrendScheduler (매시간, flag 확인)
→ Google Trends RSS 국가별 호출
→ 최대 6개 TrendDTO 구성
→ TrendService.replaceTrendKeywords
→ JSON 직렬화 완료
→ Redis SET trend:{countryCode}

GET /api/trend
→ TrendService.getTrendingKeywords
→ Redis JSON 역직렬화

GET /api/trend/summary
→ TrendCrawledService
→ 기사 본문 crawl
→ TrendAiService
→ Redis summary cache 확인
→ Gemini 호출
```

핵심 코드:

- [TrendScheduler](../../src/main/java/com/example/globalTimes_be/domain/trend/scheduler/TrendScheduler.java)
- [TrendService](../../src/main/java/com/example/globalTimes_be/domain/trend/service/TrendService.java)
- [TrendAiService](../../src/main/java/com/example/globalTimes_be/domain/trend/service/TrendAiService.java)
- [TrendServiceTest](../../src/test/java/com/example/globalTimes_be/domain/trend/service/TrendServiceTest.java)
- [TrendAiServiceTest](../../src/test/java/com/example/globalTimes_be/domain/trend/service/TrendAiServiceTest.java)

### 4.2 해결한 문제

Trend 기사 요약 prompt는 손상된 문자열이었고 외부 호출 제한 시간이 없었다. [Trend Gemini 오류 정책](trend-gemini-error-policy.md)에서 prompt를 복구하고 기존 `gemini.timeout-ms`를 재사용해 Gemini non-2xx를 502, timeout을 504, 내부 응답 처리를 500으로 구분했다. mock HTTP와 mock Redis 7개 테스트가 실제 Gemini 비용 없이 계약을 고정한다.

Trend 목록 갱신은 기존 key를 먼저 삭제한 뒤 새 JSON을 저장했다. [Trend Redis 갱신 보존](trend-redis-refresh-preservation.md)에서 선행 DELETE와 삭제 API를 제거하고, 완성된 JSON을 Redis SET 한 번으로 교체했다. 직렬화 실패는 Redis를 변경하지 않고 write 실패도 애플리케이션이 기존 key를 의도적으로 삭제하지 않는다.

### 4.3 남은 경계

- Trend AI는 동기 `.block()` 호출이다. 기사 summary 경로에서 확인한 async executor 효과를 Trend에 그대로 적용할 트래픽 근거는 아직 없다.
- DNS 실패나 연결 거부처럼 HTTP 응답 이전 transport 오류는 현재 내부 500으로 분류된다.
- mock Redis write 실패 테스트는 실제 네트워크 장애를 재현하지 않는다. 보장 범위는 DELETE를 먼저 호출하지 않는 애플리케이션 interaction이다.
- 다중 인스턴스 scheduler 중복 실행과 분산 락은 현재 단일 인스턴스 범위 밖이다.

## 5. 기사 조회·검색·Perspectives

### 5.1 일반 조회 경로

```text
GET /api/articles/latest|cursor|popular
→ ArticleService
→ ArticleRepository
→ MySQL 조회

GET /api/news/detail?id=...
→ DetailService
→ Article 조회
→ DB 원자 view_count 증가
→ summary/crawledContent 상태 반환
```

핵심 코드:

- [ArticleController](../../src/main/java/com/example/globalTimes_be/domain/article/controller/ArticleController.java)
- [ArticleService](../../src/main/java/com/example/globalTimes_be/domain/article/service/ArticleService.java)
- [DetailController](../../src/main/java/com/example/globalTimes_be/domain/detail/controller/DetailController.java)
- [DetailService](../../src/main/java/com/example/globalTimes_be/domain/detail/service/DetailService.java)
- [ArticleRepository](../../src/main/java/com/example/globalTimes_be/domain/article/repository/ArticleRepository.java)

popular 조회는 로컬 단일 인스턴스 부하에서 20 VU 약 `87.29 RPS`, p95 `105.48ms`였고 50 VU에서는 처리량이 `86.15 RPS`로 늘지 않은 채 p95가 `456.72ms`로 상승했다. 이는 운영 최대 TPS가 아니라 8GB 로컬 환경의 첫 포화 신호다.

`ORDER BY view_count DESC, published_at DESC`의 `Using filesort`는 무조건 문제라는 뜻이 아니다. [popular filesort 분석](articles-popular-filesort-analysis.md)에서 실제 query 시간과 전체 HTTP 지연을 분리했고, 현재 데이터 규모에서는 새 index가 전체 비용을 개선한다는 근거가 부족해 보류했다.

기사 상세의 조회수 증가는 read-modify-write에서 lost update가 발생했다. DB 원자 UPDATE로 바꾼 뒤 동일 20개 성공 요청의 실제 증가량이 `2 → 20`, 유실이 `18 → 0`이 됐다. 자세한 과정은 [조회수 원자 증가](atomic-view-count-consistency.md)를 따른다.

### 5.2 검색 경로

```text
GET /api/search?text=...&country=...&category=...&date=...
→ SearchController
→ TranslationService.translateToEnglish(text)
→ Redis translation:{text} cache 또는 Google Translation, 실패 시 원문 fallback
→ SearchArticlesService
→ country·category·date filter 정규화
→ 원문과 영어 번역문이 같으면 단일 검색어, 다르면 두 검색어 전달
→ ArticleRepository.searchByDescriptionOrTitleWithExploreFilters(...)
→ MySQL title·description FULLTEXT BOOLEAN MODE
→ Article DTO 반환
```

핵심 코드:

- [SearchController](../../src/main/java/com/example/globalTimes_be/domain/search/controller/SearchController.java)
- [TranslationService](../../src/main/java/com/example/globalTimes_be/domain/search/service/TranslationService.java)
- [SearchArticlesService](../../src/main/java/com/example/globalTimes_be/domain/search/service/SearchArticlesService.java)
- [ArticleRepository FULLTEXT query](../../src/main/java/com/example/globalTimes_be/domain/article/repository/ArticleRepository.java)

검색은 JPA LAZY 관계를 transaction 밖에서 접근해 500을 반환하던 문제를 먼저 해결한 뒤, MySQL `EXPLAIN ANALYZE`와 검색어별 k6 기준선을 분리했다.

일반 검색 API는 사용자가 입력한 `text` 전체와 영어 번역문 전체를 Repository에 전달한다. `KeywordExtractor`로 제목에서 최대 4개 token을 추출하는 경로는 일반 검색이 아니라 아래 Perspectives 후보 검색에만 사용된다.

- [검색 FULLTEXT 분석](search-fulltext-analysis.md): 중복 `OR MATCH` 제거와 실제 실행 계획
- [검색어별 기준선](search-fulltext-term-baseline.md): p95 `24.78~103.41ms`, 실패 0%
- 한국어·AI 표본의 0건 결과는 latency 문제가 아니라 다국어 recall 문제로 해석한다.

### 5.3 Perspectives 경로

```text
GET /api/news/{id}/perspectives
→ PerspectivesService
→ Redis perspectives:{articleId} 조회
→ 원문 제목·언어 확인
→ 비영어 제목은 TranslationService로 영어 1회 번역
→ KeywordExtractor
→ MySQL FULLTEXT 후보 검색
→ 국가별 grouping·최대 3건
→ Redis 1시간 cache
```

핵심 코드:

- [DetailController](../../src/main/java/com/example/globalTimes_be/domain/detail/controller/DetailController.java)
- [PerspectivesService](../../src/main/java/com/example/globalTimes_be/domain/detail/service/PerspectivesService.java)
- [TranslationService](../../src/main/java/com/example/globalTimes_be/domain/search/service/TranslationService.java)
- [RedisUtil](../../src/main/java/com/example/globalTimes_be/global/redis/RedisUtil.java)
- [PerspectivesMultilingualIntegrationTest](../../src/test/java/com/example/globalTimes_be/domain/detail/service/PerspectivesMultilingualIntegrationTest.java)

Phase 1에서는 성능과 검색 품질을 구분했다.

| 관점 | 확인 결과 | 해석 |
| --- | --- | --- |
| cache 성능 | cold p95 `218.65ms`, warm p95 `21.29ms` | cache hit 경로가 약 90.3% 짧지만 cold는 1회 표본 |
| FULLTEXT ordering | latest-first, relevance-first, bounded hybrid 비교 | pure relevance-first는 wrong-context 기사를 상단에 올릴 수 있음 |
| controlled 다국어 회귀 | 번역 성공·원문 fallback·중복 제거·0건 원인 5개 시나리오 | 로직 경로가 의도대로 동작함을 보장, 실제 검색 품질 보장은 아님 |
| 실제 DB labeled sample | strict Precision@5 `0.267`, Useful Precision@5 `0.467`, Hit@5 `0.500` | 고정 표본 6건의 기준선이며 전체 9,814건 정확도가 아님 |

관련 문서는 다음 순서로 읽는다.

1. [Redis cache 정책](perspectives-redis-cache-policy.md)
2. [FULLTEXT 실행 계획](perspectives-fulltext-explain.md)
3. [대표 표본 스냅샷](perspectives-matching-sample-snapshot.md)
4. [일반 토큰 필터 결과](perspectives-fulltext-generic-token-filter-result.md)
5. [ordering 비교](perspectives-fulltext-ordering-comparison.md)
6. [ranking 도입 보류 ADR](perspectives-ranking-policy-adr.md)
7. [다국어 경로 통합 테스트](perspectives-multilingual-fulltext-integration.md)
8. [실제 DB labeled quality](perspectives-labeled-quality-baseline.md)

### 5.4 Phase 2와 연결되는 이유

Perspectives는 Phase 1에서 가장 명확한 품질 한계를 수치로 남긴 도메인이다. 하지만 Precision 표본이 6건이고 candidate 자체가 0건인 사례는 collection coverage와 retrieval 원인이 분리되지 않았다.

따라서 Phase 2에서도 Vector DB를 바로 도입하지 않는다. 먼저 고정 표본을 늘리고 다음을 구분해야 한다.

1. 관련 기사가 DB에 있지만 FULLTEXT가 놓치는가?
2. 관련 기사가 애초에 수집되지 않았는가?
3. 번역 또는 keyword 추출이 검색어를 훼손했는가?
4. 후보는 맞지만 ordering이 잘못됐는가?

이 구분 뒤 semantic retrieval이 필요한 사례가 반복될 때만 embedding·Vector DB를 같은 labeled set에서 비교한다.

## 6. 기사 요약과 Gemini SSE 질의응답

### 6.1 실행 경로

```text
GET /api/ai/{id}/summary
→ 저장 summary 확인
→ crawledContent 확인 또는 ArticleCrawler
→ AiService Gemini 호출
→ summary 저장·반환

GET /api/ai/{id}/ask (SSE)
→ JWT 또는 익명 session 식별
→ 이전 context 조회
→ AiSseService Gemini streaming
→ client에 SSE 전송
→ 완료 후 로그인 MySQL 또는 익명 Redis history 저장
```

핵심 코드:

- [AiController](../../src/main/java/com/example/globalTimes_be/domain/ai/controller/AiController.java)
- [AiService](../../src/main/java/com/example/globalTimes_be/domain/ai/service/AiService.java)
- [AiSseService](../../src/main/java/com/example/globalTimes_be/domain/ai/service/AiSseService.java)
- [AiSummaryAsyncConfig](../../src/main/java/com/example/globalTimes_be/global/config/AiSummaryAsyncConfig.java)
- [ArticleCrawler](../../src/main/java/com/example/globalTimes_be/global/crawler/ArticleCrawler.java)

### 6.2 외부 호출 latency와 오류 정책

[Gemini mock latency 기준선](gemini-mock-latency-baseline.md)에서 mock 200ms/1s/3s가 사용자 p95 약 `263~290ms/1.04~1.06초/3.04~3.05초`로 전달됨을 확인했다. 외부 지연이 전체 응답을 지배한다는 뜻이지 실제 Gemini 성능 측정은 아니다.

[Gemini timeout·오류 정책](gemini-timeout-upstream-error-policy.md)에서 90초 고정 대기를 설정형 10초로 낮췄다.

- 15초 mock p95 `16.08초 → 10.42초`
- mock 500은 약 0.31초에 backend 502
- timeout은 backend 504
- 내부 처리 오류는 500

### 6.3 Servlet thread 격리

동기 Gemini 20 VU에서 Tomcat busy thread가 `20/20`에 도달했고 동시에 실행한 popular API p95가 `154.46ms → 2.86초`, RPS가 `24.55 → 3.71`로 악화됐다. DB query 자체보다 request thread 대기가 다른 API에 전파된 결과였다.

`WebAsyncTask + bounded executor` 적용 후 50 VU에서 summary 처리량은 약 6.29 RPS로 비슷했지만 popular API는 다음처럼 회복했다.

```text
p95 5.97초 → 172.98ms
RPS 0.91 → 22.40
Tomcat busy peak 20/20 → 8/20
```

이 변경은 Gemini 자체를 빠르게 만든 것이 아니다. 느린 외부 호출 worker를 Servlet request thread에서 격리해 다른 API를 보호한 것이다.

executor를 5 workers/queue 10으로 축소한 검증에서는 20 VU부터 503 fast rejection이 발생했고 queue peak는 10을 넘지 않았다. rejected p95는 약 `34~50ms`였고 부하 종료 후 25/25 요청이 다시 200으로 회복했다. 빠른 503은 처리량 향상이 아니라 overload protection이다.

관련 문서:

1. [Gemini mock latency](gemini-mock-latency-baseline.md)
2. [Servlet thread 포화](gemini-servlet-thread-saturation-baseline.md)
3. [async 전후 비교](gemini-servlet-async-comparison.md)
4. [executor 포화 보호](gemini-executor-overload-protection.md)

## 7. 채팅 이력과 Redis 동시성

### 7.1 로그인 사용자

로그인 사용자의 채팅 이력은 MySQL `ChatHistory`에 저장된다.

```text
AiSseService 완료 callback
→ ChatHistoryService.save (@Transactional)
→ ChatHistoryRepository
→ transaction commit
```

SSE 답변을 이미 사용자에게 전달한 뒤 history 저장이 실패할 수 있으므로 저장은 best-effort 부가 기능으로 정의했다. transaction 메서드 내부에서 예외를 삼키면 메서드 반환 뒤 발생하는 flush·commit 실패를 관찰하지 못한다. 저장 예외를 proxy 밖의 `AiSseService` callback에서 잡아 현재 SSE 완료와 이력 저장 실패를 분리했다.

자세한 내용은 [SSE 저장 transaction 경계](chat-history-sse-persistence-boundary.md)를 읽는다.

핵심 코드:

- [ChatHistoryService](../../src/main/java/com/example/globalTimes_be/domain/chat/service/ChatHistoryService.java)
- [ChatHistoryRepository](../../src/main/java/com/example/globalTimes_be/domain/chat/repository/ChatHistoryRepository.java)
- [ChatHistorySaveIntegrationTest](../../src/test/java/com/example/globalTimes_be/domain/chat/service/ChatHistorySaveIntegrationTest.java)

### 7.2 채팅 목록 N+1

기존 팝업 목록은 5,000건 전체 이력을 entity로 적재하고 기사별 최신 행을 Java에서 고른 뒤 Article LAZY 접근으로 N+1을 발생시켰다.

MySQL 8 `ROW_NUMBER()` projection으로 사용자·기사별 최신 1건을 DB에서 선택한 결과:

```text
SQL 101 → 1
entity loads 5,100 → 0
service elapsed 83.0~86.8% 감소
```

원본은 [latest-per-article query](chat-history-latest-query.md)와 [PR #215](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/215)이다.

### 7.3 익명 사용자

익명 사용자는 JWT userId 대신 sessionId로 구분하며 Redis에 대화를 저장한다. 기존 String JSON 전체 read-modify-write는 같은 session의 동시 완료가 서로의 값을 덮어쓸 수 있었다.

Redis List의 `RPUSH`로 turn을 추가하고 Sorted Set으로 최근 기사 인덱스를 관리하도록 변경했다. `ZADD GT`는 늦게 도착한 과거 score가 최신 활동 시각을 되돌리지 못하게 한다.

통제된 동시 20건에서 대화 보존이 `1 → 20`, 유실이 `19 → 0`이 됐고 서로 다른 기사 인덱스도 `20/20` 보존됐다. Lua나 MULTI/EXEC 전체 transaction은 도입하지 않았다.

원본은 [익명 채팅 Redis 동시성](anonymous-chat-redis-concurrency.md)과 [PR #222](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/222)이다.

핵심 코드:

- [AnonymousChatSessionService](../../src/main/java/com/example/globalTimes_be/domain/chat/service/AnonymousChatSessionService.java)
- [AnonymousChatSessionServiceRedisIntegrationTest](../../src/test/java/com/example/globalTimes_be/domain/chat/service/AnonymousChatSessionServiceRedisIntegrationTest.java)

## 8. 스크랩·인증·사용자 데이터 접근

### 8.1 인증 경계

`SecurityConfig`는 공개 API와 인증 필요 API를 matcher로 구분하고 `JwtAuthenticationFilter`가 JWT를 해석한다.

- 일반 API는 `Authorization: Bearer <JWT>`를 사용한다.
- browser `EventSource` 기반 SSE는 임의 Authorization header 설정이 제한되어 `GET /api/ai/{id}/ask`에서만 query token을 허용한다.
- 다른 URL의 query token은 인증 수단으로 사용하지 않는다.
- 사용자 데이터 접근은 request parameter userId가 아니라 JWT subject의 userId를 기준으로 한다.

관련 PR은 [#201](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/201), [#205](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/205)다.

핵심 코드:

- [SecurityConfig](../../src/main/java/com/example/globalTimes_be/global/config/SecurityConfig.java)
- [JwtAuthenticationFilter](../../src/main/java/com/example/globalTimes_be/global/security/JwtAuthenticationFilter.java)
- [SecurityConfigTest](../../src/test/java/com/example/globalTimes_be/global/config/SecurityConfigTest.java)
- [JwtAuthenticationFilterTest](../../src/test/java/com/example/globalTimes_be/global/security/JwtAuthenticationFilterTest.java)

### 8.2 스크랩 목록 조회

로그인 목록은 Scrap→Article→Source LAZY 관계로 SQL `201`, entity load `300`이 발생했다. 비로그인 호환 목록도 200개 ID를 개별 조회했다.

DTO projection 일괄 조회와 요청 ID 순서 복원으로 다음 결과를 얻었다.

```text
로그인: SQL 201 → 1, entity 300 → 0
비로그인: SQL 200 → 1, entity 200 → 0
```

자세한 query와 fixture는 [스크랩 목록 최적화](scrap-list-query-optimization.md)를 읽는다.

### 8.3 스크랩 toggle 동시성

동일 user/article에 20개 POST toggle이 동시에 도착하면 unique INSERT 경쟁으로 성공 2건·실패 18건이 발생했다. user row를 `PESSIMISTIC_WRITE`로 잠가 같은 사용자의 toggle을 직렬화했다.

```text
성공 2 → 20
실패 18 → 0
true 10 / false 10
최종 scrap 0건
```

서로 다른 사용자는 같은 기사를 동시에 스크랩할 수 있으므로 article 전역 잠금은 사용하지 않았다. Redis 분산 락도 현재 단일 DB 정합성 문제에는 필요하지 않았다. 원본은 [스크랩 toggle 동시성](scrap-toggle-concurrency.md)이다.

핵심 코드:

- [ScrapController](../../src/main/java/com/example/globalTimes_be/domain/scrap/controller/ScrapController.java)
- [ScrapService](../../src/main/java/com/example/globalTimes_be/domain/scrap/service/ScrapService.java)
- [ScrapRepository](../../src/main/java/com/example/globalTimes_be/domain/scrap/repository/ScrapRepository.java)
- [ScrapQueryIntegrationTest](../../src/test/java/com/example/globalTimes_be/domain/scrap/service/ScrapQueryIntegrationTest.java)

## 9. Flyway·Testcontainers·CI

### 9.1 schema 재현

Flyway는 데이터 자체가 아니라 schema 변경 순서를 versioned SQL로 관리한다.

| migration | 역할 |
| --- | --- |
| V1 | 현재 5개 도메인 table 기준선 생성 |
| V2 | legacy 객체 이름, FK와 FULLTEXT를 canonical 구조로 수렴 |
| V3 | 기존 URL 중복 정리와 URL hash UNIQUE 적용 |

Hibernate는 `validate`로 entity와 schema 불일치를 확인한다. 빈 MySQL과 legacy baseline 모두 V1→V3 또는 기존 version→V3 경로를 Testcontainers에서 재현한다.

핵심 파일:

- [V1 baseline schema](../../src/main/resources/db/migration/V1__baseline_schema.sql)
- [V2 schema normalization](../../src/main/resources/db/migration/V2__normalize_schema_objects.sql)
- [V3 URL uniqueness](../../src/main/resources/db/migration/V3__enforce_article_url_uniqueness.sql)
- [ArticleRepositoryIntegrationTest](../../src/test/java/com/example/globalTimes_be/domain/article/repository/ArticleRepositoryIntegrationTest.java)
- [Backend CI workflow](../../.github/workflows/backend-ci.yml)

### 9.2 테스트 계층

| 계층 | 예시 | 확인하는 것 |
| --- | --- | --- |
| Mockito 단위 테스트 | `TrendServiceTest`, `AiServiceTest` | 호출 순서, fallback, HTTP status mapping |
| mock HTTP + Service | News API·Gemini tests | 200·5xx·delay·timeout 반복 재현 |
| MySQL Testcontainers | repository·collection·chat·scrap | 실제 MySQL SQL, constraint, transaction, EXPLAIN |
| Redis Testcontainers | anonymous chat | 실제 Redis List·Sorted Set 동시 명령 |
| k6 | `load-tests/k6/*.js` | HTTP RPS, p95, failure, VU/arrival-rate |
| Backend CI | `.github/workflows/backend-ci.yml` | develop PR/push마다 전체 Gradle test |

Testcontainers는 실제 개발 DB의 복사본이 아니다. test마다 필요한 fixture를 생성한 임시 MySQL/Redis container이며 구조적 회귀를 반복 가능하게 만든다.

### 9.3 현재 CI/CD 상태

현재 활성 workflow는 `Backend CI` 하나다.

```text
develop PR 또는 develop push
→ GitHub-hosted Ubuntu runner
→ JDK 17
→ ./gradlew test --no-daemon
→ Testcontainers 포함 전체 회귀 테스트
```

해커톤 당시 DockerHub와 EC2 자동 배포 파이프라인은 현재 대상 EC2가 삭제돼 비활성 상태다. Phase 1에서는 동작하지 않는 legacy CD를 유지하는 대신 secrets가 필요 없는 CI를 분리했다. 새 배포 환경과 CD는 이번 범위가 아니다.

## 10. 부하 테스트 수치를 읽는 법

### 10.1 VU와 RPS

VU는 동시에 행동하는 가상 사용자 수다. RPS는 1초 동안 서버에 도착하거나 완료한 HTTP 요청 수다. 응답을 기다린 뒤 다음 요청을 보내는 closed model에서는 같은 VU라도 응답이 느려지면 RPS가 감소한다.

arrival-rate는 응답 완료 여부와 별개로 목표 RPS에 맞춰 요청 도착을 시도한다. 따라서 서버가 따라가지 못하면 필요한 VU가 늘거나 dropped iteration이 발생한다.

### 10.2 로컬 결과의 범위

- 120 RPS는 확인한 최대 단계이며 운영 최대 TPS가 아니다.
- 8GB 노트북의 CPU·memory·Docker 상태가 결과에 포함된다.
- mock Gemini latency는 외부 지연을 통제하기 위한 값이며 실제 Gemini SLA가 아니다.
- 단일 인스턴스 결과를 Pod 수로 단순 곱한 값은 실제 scale-out 성능이 아니다.
- p95만 보지 않고 Actuator, application 단계 로그, MySQL query와 container resource를 같은 구간에서 해석한다.

실행 방법은 [부하 테스트 기준선](load-test-baseline.md)과 [로컬 관측 runbook](local-load-observability-runbook.md)을 따른다.

## 11. Phase 1 완료 판단

Phase 1은 기존 단일 인스턴스 구조의 기본 정확성·성능·복구 가능성을 설명할 수 있는 상태를 목표로 했다.

| 축 | 완료 근거 | 남은 한계 |
| --- | --- | --- |
| MySQL query | FULLTEXT EXPLAIN, chat/scrap N+1 제거, 원자 view count | 운영 장기 데이터 분포와 고정 자원 capacity |
| Redis | Perspectives cache 정책, 익명 chat 동시성, Trend stale 보존 | multi-node Redis, 전체 명령 transaction |
| 외부 호출 | crawler timeout, Gemini mock·timeout·502/504, async 격리 | 실제 provider SLA, Trend transport 오류 |
| 수집 | 중복 방지, DB UNIQUE, 부분 실패 E2E, batch 처리량 | 장기 backlog, replay, multi-instance scheduler |
| 정합성 | transaction rollback, scrap toggle lock, SSE 저장 실패 가시성 | outbox·retry가 필요한 반복 실패 근거 |
| 인증 | protected matcher, JWT userId, SSE query token 제한 | 배포 환경의 실제 OAuth·secret 운영 |
| 재현성 | Flyway V1~V3, MySQL/Redis Testcontainers, Backend CI | 실제 staging·production CD |

위 항목은 Phase 1 범위에서 완료로 판단한다. 이는 시스템에 더 이상 문제가 없다는 뜻이 아니라, 현재 구조의 주요 문제와 한계가 코드·테스트·수치·문서로 연결됐다는 뜻이다.

## 12. Phase 2 진입 기준

Phase 2는 기술 목록을 구현하는 단계가 아니라 Phase 1에서 남긴 한계 중 하나를 더 큰 데이터나 고정 환경에서 다시 검증하는 단계다.

| 후보 | 현재 근거 | 먼저 확인할 신호 | 기술 검토 조건 |
| --- | --- | --- | --- |
| Perspectives 검색 품질 | 6개 표본 strict P@5 0.267, candidate 0 사례 | 표본 확대와 collection/retrieval/ranking 원인 분리 | 관련 기사가 DB에 있는데 FULLTEXT가 반복 누락될 때 embedding·Vector DB 비교 |
| 고정 자원 capacity | 로컬 100 RPS부터 DB CPU·Hikari pressure | CPU·memory 제한 container/instance에서 동일 시나리오 재현 | 단일 capacity 확정 뒤 다중 instance·Pod 효율 직접 비교 |
| 수집 queue | 1,000건 batch가 scheduler 주기보다 충분히 짧음 | backlog, replay, 장시간 source 지연, 독립 consumer 요구 | 동기 수집로 복구가 어려울 때 Kafka·durable queue 비교 |
| 외부 API resilience | timeout·오류 분리와 executor 보호 완료 | transport 장애 빈도, retry 가능한 idempotent 경계 | 실패율·복구 목표가 정의될 때 retry·circuit breaker |
| Redis 분산 정합성 | 단일 Redis 명령과 DB lock으로 현재 문제 해결 | multi-instance 경쟁과 부분 실패 재현 | local lock/명령으로 보장할 수 없을 때 분산 락·Lua 검토 |

현 시점의 가장 강한 제품 문제 근거는 Perspectives 품질 기준선이다. 다만 첫 Phase 2 작업은 Vector DB 구현이 아니라 labeled sample 확대와 원인 분리여야 한다.

## 13. 권장 학습 순서

Phase 1의 작업이 왜 다음 단계로 이어졌는지 시간 순으로 학습하려면 [Backend Phase 1 작업·학습 여정](backend-phase1-learning-journey.md)을 먼저 읽는다. 아래 목록은 관심 개념별 원본 문서 읽기 순서다.

### A. 성능 개선 흐름

1. [부하 테스트 기준선](load-test-baseline.md)
2. [단일 인스턴스 TPS 기준선](single-instance-tps-baseline.md)
3. [mixed arrival-rate](mixed-arrival-rate-baseline.md)
4. [mixed saturation](mixed-saturation-boundary.md)
5. [Gemini thread 포화](gemini-servlet-thread-saturation-baseline.md)
6. [async 전후 비교](gemini-servlet-async-comparison.md)
7. [executor overload 보호](gemini-executor-overload-protection.md)

### B. DB·정합성 흐름

1. [Perspectives FULLTEXT EXPLAIN](perspectives-fulltext-explain.md)
2. [조회수 원자 증가](atomic-view-count-consistency.md)
3. [chat latest query](chat-history-latest-query.md)
4. [scrap list query](scrap-list-query-optimization.md)
5. [scrap toggle 동시성](scrap-toggle-concurrency.md)
6. [Flyway 기준선](flyway-schema-baseline.md)
7. [기사 URL 유일성](article-url-uniqueness.md)

### C. 외부 API·실패 복구 흐름

1. [기사 crawl latency](article-crawl-latency-baseline.md)
2. [Gemini mock latency](gemini-mock-latency-baseline.md)
3. [Gemini timeout·오류 정책](gemini-timeout-upstream-error-policy.md)
4. [News API 실패 E2E](news-api-ingestion-failure-e2e.md)
5. [SSE 저장 transaction 경계](chat-history-sse-persistence-boundary.md)
6. [Trend Gemini 정책](trend-gemini-error-policy.md)
7. [Trend Redis 갱신 보존](trend-redis-refresh-preservation.md)

### D. 검색 품질 흐름

1. [대표 표본 스냅샷](perspectives-matching-sample-snapshot.md)
2. [source coverage 한계](perspectives-source-coverage-limit-log.md)
3. [FULLTEXT ordering 비교](perspectives-fulltext-ordering-comparison.md)
4. [ranking 보류 ADR](perspectives-ranking-policy-adr.md)
5. [다국어 통합 테스트](perspectives-multilingual-fulltext-integration.md)
6. [labeled quality 기준선](perspectives-labeled-quality-baseline.md)

## 14. 문서 역할 구분

| 문서 | 역할 |
| --- | --- |
| 이 문서 | 코드 구조와 Phase 1 문제·해결·근거를 탑다운으로 연결 |
| [작업·학습 여정](backend-phase1-learning-journey.md) | 문제 발견부터 다음 작업 선택까지의 이유·개념·수행·검증을 시간 순으로 연결 |
| [정량 인덱스](backend-improvement-quantitative-index.md) | 개선 전후 수치와 기준선을 빠르게 찾는 표 |
| [WORK_PROGRESS](WORK_PROGRESS.md) | Issue 순서와 상세 작업 이력 |
| [BACKLOG](BACKLOG.md) | 완료·보류·후속 후보와 overengineering 판단 |
| [NEXT_AGENT_BRIEF](NEXT_AGENT_BRIEF.md) | 새 세션에서 현재 상태를 빠르게 복원 |
| 개별 문서 | 실행 조건, fixture, raw result, 해석 한계의 원본 |

Issue #110은 장기 troubleshooting 기록이며 Phase 1 종료 범위에 포함하지 않는다.

## 15. 문서 검증 결과

- README, 구조 맵과 정량 인덱스의 상대 Markdown·Java·SQL·workflow 링크 150개 파일 존재 확인
- 일반 검색 API를 포함한 주요 실행 경로를 현재 Controller·Service·Repository 코드와 대조
- #223 이후 정량 결과를 원본 측정 문서·WORK_PROGRESS·BACKLOG와 교차 확인
- `git diff --check` 및 GitHub Backend CI 전체 Gradle test 통과
- AI Reviewer의 일반 검색 경로 Blocking을 수정하고 최신 HEAD 재검토에서 Blocking 없음·MERGE_READY 확인

위 검증과 11절의 완료 기준에 따라 Backend Phase 1을 완료로 판단한다.
