# 백엔드 개선 작업 진척 노트

이 문서는 GlobalTimes 백엔드 개선 작업을 장기적으로 이어가기 위한 작업 진척 노트다.
Codex 대화 context가 사라지거나 새 세션에서 이어서 작업해야 할 때, 이 문서를 기준으로 현재까지의 의사결정, 완료 작업, 다음 작업을 복원한다.

## 1. 운영 원칙

### 기본 개발 흐름

모든 작업은 아래 순서로 진행한다.

```text
Issue 생성
→ 작업 브랜치 생성
→ 조사
→ 계획 제안
→ 사용자 승인
→ 구현
→ 테스트/검증
→ PR 생성
→ AI Reviewer 검토
→ Blocking 반영
→ 최종 Blocking 없음 확인
→ 사용자 승인 후 merge
```

### 역할 분리

현재는 VS Code Codex extension의 대화창 기반으로 역할을 분리한다.

| 역할 | 수행 위치 | 책임 |
| --- | --- | --- |
| Implementer / Planner | 현재 Codex 대화창 | 조사, 계획, 구현, 테스트, PR 생성, 리뷰 반영 |
| Reviewer | 별도 Codex 대화창 | PR diff 검토, Blocking/Non-blocking 분류, PR comment 작성 |
| User | 사용자 | 승인 게이트, 범위 판단, merge 승인 |

Reviewer는 코드 수정, 커밋, push, merge를 하지 않는다.
Reviewer는 `## AI Reviewer 검토 결과` 제목으로 GitHub PR comment를 남긴다.

### 커밋 메시지 규칙

Conventional Commit type은 영어로 쓰고, 설명은 한국어로 작성한다.

예시:

```text
docs: AI Reviewer PR Comment Workflow 문서화
perf: 주요 API 부하 테스트 기준선 추가
fix: 검색 API LazyInitializationException 수정
chore: AI Reviewer Blocking 확인 스크립트 추가
```

### PR 리뷰 판정 기준

- `Blocking`: merge 전에 반드시 수정해야 하는 문제
- `Non-blocking`: 이번 PR에서 선택적으로 반영하거나 후속 이슈로 분리 가능한 개선점

Blocking 예시:

- 테스트 실패 또는 컴파일 오류
- 보안/민감 정보 로그 노출
- API 응답 구조의 의도치 않은 변경
- DB 데이터 손상 가능성
- Issue 범위를 벗어난 변경
- 문서에 명시한 원칙과 코드가 충돌

## 2. 지금까지 완료한 작업

### 현재까지의 큰 흐름 요약

- #113/#114에서 백엔드 개선 backlog와 AI 협업 운영 규칙을 만들었다.
- #115/#116에서 주요 API의 성능 관측 로그를 추가했고, 민감 정보 로그 노출은 Reviewer Blocking으로 잡아 같은 PR에서 수정했다.
- #117/#118, #119/#120에서 Reviewer comment workflow와 Blocking 확인 스크립트를 정리했다.
- #121/#122에서 k6 기반 주요 API 부하 테스트 기준선을 만들었고, 이 과정에서 `/api/search` 500 응답을 발견했다.
- #123/#126에서 검색 API `LazyInitializationException`을 수정하고, 동일 k6 smoke 조건에서 `http_req_failed`를 16.25%에서 0.00%로 낮췄다.
- #127/#128에서 Perspectives API cold/warm Redis cache 부하 테스트를 분리해 cold p95 218.65ms, warm p95 21.29ms를 기록했다.
- #129/#130에서 Perspectives Redis cache key/TTL/fallback/stale 허용 기준을 문서화했다.
- #131/#132에서 Perspectives FULLTEXT 쿼리의 `EXPLAIN`/`EXPLAIN ANALYZE`를 기록했고, 현재 데이터 규모에서는 FULLTEXT 인덱스 사용을 확인했다.
- #133/#134에서 검색 API FULLTEXT 실행 계획을 분석하고, 원문/번역 검색어가 같은 경우 중복 `OR MATCH`를 제거해 FULLTEXT 인덱스를 사용하도록 개선했다.
- #137/#138에서 기사 원문 크롤링 timeout/fallback과 외부 I/O 트랜잭션 분리를 개선했다.
- #140/#141에서 Perspectives API 캐시 hit/miss, Redis 실패, 번역 fallback 흐름을 회귀 테스트로 고정했다.
- 현재 반복 성능/안정성 기본기 흐름의 주요 후보(#123, #127, #129, #131, #133, #137, #140)는 merge 완료 상태다.
- #142에서는 Perspectives 다국어 이슈 매칭 품질 기준선을 정의해, Elasticsearch/Vector DB/RAG 같은 기술 도입 전에 현재 FULLTEXT 기반 매칭의 한계를 측정 가능하게 만든다.

### #113 / PR #114 - 백엔드 개선 Backlog 및 AI 작업 운영 규칙 수립

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/113
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/114
- 상태: merged
- 주요 파일:
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/ai-workflow/README.md`

작업 내용:

- 백엔드 개선 작업을 Issue, Branch, PR, Merge 흐름으로 관리하도록 문서화했다.
- AI를 단순 코드 생성 도구가 아니라 조사자, 계획자, 구현자, 검토자로 역할 분리해 사용하는 규칙을 정리했다.
- 사용자 승인 전에는 코드, 설정, DB, 배포 작업을 하지 않는 승인 게이트를 명시했다.

의미:

- AI 작업을 통제 가능한 개발 프로세스 안에 넣는 첫 단계다.
- 이후 모든 개선 작업의 기준 문서가 되었다.

---

### #115 / PR #116 - 주요 API 성능 관측 로그 추가

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/115
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/116
- 상태: merged
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/domain/article/service/ArticleService.java`
  - `src/main/java/com/example/globalTimes_be/domain/article/service/ExploreService.java`
  - `src/main/java/com/example/globalTimes_be/domain/detail/service/PerspectivesService.java`
  - `src/main/java/com/example/globalTimes_be/domain/search/service/SearchArticlesService.java`
  - `src/main/java/com/example/globalTimes_be/domain/search/service/TranslationService.java`
  - `src/main/java/com/example/globalTimes_be/global/translate/TranslateUtil.java`
  - `docs/backend-improvement/performance-observability-baseline.md`

작업 내용:

- 주요 API의 처리 시간과 병목 후보를 로그로 확인할 수 있도록 관측 로그를 추가했다.
- `PerspectivesService`에 캐시 hit/miss, 기준 기사 조회 시간, 키워드 추출 시간, 번역 여부, FULLTEXT 검색 시간, 국가/기사 수, 전체 처리 시간을 기록했다.
- `SearchArticlesService`, `TranslationService`, `ArticleService`, `ExploreService`에 DB 조회 시간, 결과 수, 번역 캐시 여부, 외부 번역 API 호출 시간 등을 기록했다.
- 검색어와 번역 결과 원문은 로그에 남기지 않고 길이만 기록하도록 수정했다.

AI Reviewer로 발견한 문제:

- 처음에는 `plainKeyword`, `translated` 원문이 info 로그에 남아 있었다.
- `TranslateUtil`에도 원문/번역 결과 로그가 남아 있었다.
- Reviewer가 이를 Blocking으로 지적했고, 같은 PR에서 수정했다.

검증:

```text
./gradlew.bat test
git diff --check
```

의미:

- 성능 개선을 감으로 말하지 않고, 이후 p95/오류율/캐시 hit 여부와 연결해 분석할 기반을 만들었다.
- 민감 정보 로그 노출을 Reviewer가 잡아내고 수정한 사례가 남았다.

---

### #117 / PR #118 - AI Reviewer PR Comment Workflow 문서화

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/117
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/118
- 상태: merged
- 주요 파일:
  - `docs/ai-workflow/reviewer-comment-workflow.md`
  - `docs/ai-workflow/README.md`

작업 내용:

- Implementer 세션과 Reviewer 세션 사이에서 사용자가 리뷰 결과를 매번 복사하는 부담을 줄이기 위해, GitHub PR comment를 중심으로 리뷰 결과와 반영 내역을 기록하는 반자동 흐름을 문서화했다.
- Reviewer는 PR diff 검토와 PR comment 작성만 수행하고, 코드 수정/커밋/push/merge는 금지하도록 명확히 했다.

의미:

- 완전 자동화가 아니라, 먼저 GitHub PR을 AI 협업의 단일 기록 저장소로 표준화한 단계다.
- 이후 스크립트/MCP 자동화의 문제 정의가 되었다.

---

### #119 / PR #120 - AI Reviewer Blocking 확인 스크립트 추가

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/119
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/120
- 상태: merged
- 주요 파일:
  - `scripts/ai-workflow/check-review-blocking.ps1`
  - `docs/ai-workflow/review-blocking-check-script.md`
  - `docs/ai-workflow/reviewer-comment-workflow.md`

작업 내용:

- 최신 AI Reviewer comment에서 `Blocking`, `Non-blocking`, `결론` 섹션을 추출하는 PowerShell 스크립트를 추가했다.
- 결과를 아래 상태로 출력한다.

| 상태 | 의미 |
| --- | --- |
| `MERGE_READY` | Blocking 없음 |
| `CHANGES_REQUIRED` | Blocking 있음 |
| `REVIEW_NOT_FOUND` | AI Reviewer comment 또는 Blocking 섹션을 찾지 못함 |

사용 예시:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\ai-workflow\check-review-blocking.ps1 -PrNumber 122
```

Reviewer가 잡아낸 문제와 반영:

- Implementer comment를 Reviewer comment로 오탐할 수 있는 문제를 발견했다.
- `## AI Reviewer 검토 결과` 제목 형식만 Reviewer comment 후보로 잡도록 강화했다.
- `### Blocking` 섹션이 없으면 안전하게 `REVIEW_NOT_FOUND`로 실패하도록 수정했다.
- GitHub comment 앞 BOM/공백을 허용했다.
- Windows PowerShell 5.1의 UTF-8 파싱 차이를 고려해 일부 한글 판정을 유니코드 코드포인트 기반으로 처리했다.

의미:

- AI Reviewer comment를 사람이 일일이 읽지 않고, merge 전 상태를 빠르게 확인할 수 있게 했다.
- MCP 서버로 가기 전 단계의 작은 자동화다.

---

### #121 / PR #122 - 주요 API 부하 테스트 기준선 측정

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/121
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/122
- 상태: merged
- 주요 파일:
  - `load-tests/k6/api-baseline.js`
  - `docs/backend-improvement/load-test-baseline.md`
  - `docs/backend-improvement/BACKLOG.md`

작업 내용:

- k6 기반 주요 API 부하 테스트 시나리오를 추가했다.
- 대상 API:
  - `GET /api/articles/latest`
  - `GET /api/articles/cursor`
  - `GET /api/articles/popular`
  - `GET /api/articles/explore`
  - `GET /api/search`
  - `GET /api/news/{id}/perspectives`
- 목적은 성능 개선 자체가 아니라 개선 전 기준선 측정이다.
- 과다 트래픽 대응은 이번 PR에서 구현하지 않고, rate limit, cache, 비동기 처리 등 후속 개선 후보로 분리했다.

k6 설치 및 검증:

- k6 설치 경로: `C:\Program Files\k6\k6.exe`
- 버전: `k6.exe v2.1.0`
- 검증:

```powershell
node --check load-tests/k6/api-baseline.js
& "C:\Program Files\k6\k6.exe" inspect .\load-tests\k6\api-baseline.js
git diff --check
```

Smoke test 실행 조건:

```text
BASE_URL=http://localhost:8080
ARTICLE_ID=8449
SEARCH_TEXT=대구
ARTICLES_VUS=1
SEARCH_VUS=1
PERSPECTIVES_VUS=1
ARTICLES_DURATION=15s
SEARCH_DURATION=15s
PERSPECTIVES_DURATION=15s
```

Smoke test 결과:

| API/시나리오 | 평균 응답 시간 | p95 응답 시간 | 오류율 | 비고 |
| --- | --- | --- | --- | --- |
| 전체 | 28.38ms | 49.29ms | 0.00% | 86 requests |
| articles | 28.9ms | 48.84ms | 0.00% | latest, cursor, popular, explore |
| search | 41.77ms | 61.1ms | 0.00% | `SEARCH_TEXT=대구` |
| perspectives | 13.05ms | 15.86ms | 0.00% | 반복 호출 기준 |

추가 발견:

- `SEARCH_TEXT=war`, `korea`, `economy`, `technology` 요청 시 `/api/search`에서 500 응답 발생
- 서버 로그에서 `LazyInitializationException` 확인
- 해당 문제는 부하 테스트 스크립트 문제가 아니라 검색 API 버그로 판단
- 후속 이슈 #123으로 분리

의미:

- 단순히 “부하 테스트를 했다”가 아니라, 부하 테스트 과정에서 실제 API 실패 케이스를 발견했다.
- 이후 #123에서 수정 후 동일 k6 smoke test를 재실행하면 오류율 개선을 수치로 기록할 수 있다.

## 3. 현재 바로 이어서 해야 할 작업

### #123 / PR #126 - 검색 API LazyInitializationException으로 인한 500 응답 수정

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/123
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/126
- 상태: merged
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/domain/search/service/SearchArticlesService.java`

문제:

- `/api/search?text=war`, `korea`, `economy`, `technology` 요청에서 500 응답이 발생했다.
- 서버 로그에서 `LazyInitializationException`이 확인되었다.
- `Article.source`가 `FetchType.LAZY`이고 `spring.jpa.open-in-view=false`인 상태에서, 검색 결과 DTO 변환 중 `article.getSource().getSourceName()` 접근이 영속성 컨텍스트 밖에서 발생할 수 있었다.

수정:

- `SearchArticlesService#getSearchArticles()`에 `@Transactional(readOnly = true)`를 추가했다.
- 검색 쿼리와 응답 DTO 구조는 변경하지 않았다.
- N+1, fetch join, DTO projection 개선은 이번 PR에 섞지 않고 후속 성능 이슈 후보로 남겼다.

검증:

```text
./gradlew.bat test
git diff --check
/api/search?text=war -> 200
/api/search?text=korea -> 200
/api/search?text=economy -> 200
/api/search?text=technology -> 200
```

k6 smoke test:

```text
BASE_URL=http://localhost:8080
ARTICLE_ID=8449
SEARCH_TEXT=war
ARTICLES_VUS=1
SEARCH_VUS=1
PERSPECTIVES_VUS=1
ARTICLES_DURATION=15s
SEARCH_DURATION=15s
PERSPECTIVES_DURATION=15s
```

결과:

| API/시나리오 | p95 응답 시간 | 오류율 | 비고 |
| --- | --- | --- | --- |
| 전체 | 63.9ms | 0.00% | 86 requests |
| articles | 45.21ms | 0.00% | latest, cursor, popular, explore |
| search | 64.12ms | 0.00% | `SEARCH_TEXT=war` |
| perspectives | 59.97ms | 0.00% | 반복 호출 기준 |

의미:

- #121/#122 smoke 기준 `SEARCH_TEXT=war` 조건에서 기록된 `http_req_failed=16.25%`가 #123 수정 후 `0.00%`로 개선되었다.
- 부하 테스트 과정에서 발견한 실제 API 버그를 수정하고, 동일 조건으로 재측정해 개선을 수치로 남겼다.

---

### #127 - Perspectives API cold/warm cache 부하 테스트 분리

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/127
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/128
- 상태: merged
- 작업 브랜치: `perf/#127-perspectives-cache-load-test`

목표:

- 기존 `api-baseline.js`의 `perspectives_repeated`는 Redis cache miss 첫 요청과 cache hit 반복 요청을 하나의 평균/p95에 섞어 기록한다.
- `GET /api/news/{id}/perspectives`의 cold cache와 warm cache를 분리 측정해 Redis 캐시가 반복 조회 성능을 얼마나 개선하는지 수치화한다.

현재 확인한 구조:

- Redis key: `perspectives:article:{articleId}`
- TTL: `perspectives.cache-ttl-seconds`, 기본 3600초
- cache hit 로그: `cacheHit=true`, `cacheReadMs`, `totalMs`, `countriesFound`, `totalArticles`
- cache miss 로그: `cacheHit=false`, `baseLookupMs`, `keywordMs`, `translationMs`, `translatedSearchMs`, `originalSearchMs`, `totalMs`

작업 범위:

- Perspectives 전용 k6 스크립트 추가
- cold/warm cache 측정 조건 문서화
- 측정 결과 기록 템플릿 추가
- 실제 Redis TTL/key 정책 변경, 로컬 캐시 도입, FULLTEXT 쿼리 개선, semantic similarity 개선은 후속 이슈로 분리

초기 smoke 측정:

```text
BASE_URL=http://localhost:8080
ARTICLE_IDS=8449
WARM_ARTICLE_ID=8449
COLD_VUS=1
COLD_ITERATIONS=1
WARM_VUS=1
WARM_DURATION=15s
```

실행 전 Redis key `perspectives:article:8449`를 삭제했다.

| API/시나리오 | 평균 응답 시간 | p95 응답 시간 | 오류율 | 비고 |
| --- | --- | --- | --- | --- |
| 전체 | 30.84ms | 71.49ms | 0.00% | 16 requests |
| cold cache | 218.65ms | 218.65ms | 0.00% | 1 request |
| warm cache | 18.32ms | 21.29ms | 0.00% | 15 requests |

의미:

- 동일 articleId 기준 warm cache p95가 cold cache p95보다 크게 낮았다.
- Redis cache hit가 반복 조회 응답 시간을 줄인다는 초기 근거를 확보했다.
- cold cache는 1회 샘플이므로, 더 안정적인 비교가 필요하면 여러 articleId 대상으로 반복 측정한다.

주의:

- 현재 Perspectives 관련 기사 탐색은 제목 기반 키워드 추출과 MySQL FULLTEXT 검색에 의존한다.
- 이는 진정한 의미의 semantic similarity가 아니므로, 같은 사건이라도 표현이 다르면 누락될 수 있다.
- 이번 작업은 검색 품질 개선이 아니라 cache hit/miss 성능 차이를 분리 측정하는 작업이다.

---

### #129 - Perspectives API Redis 캐시 정책 및 정합성 점검

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/129
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/130
- 상태: merged
- 작업 브랜치: `perf/#129-perspectives-redis-cache-policy`
- 주요 파일:
  - `docs/backend-improvement/perspectives-redis-cache-policy.md`

목표:

- #127에서 확인한 Redis warm cache 효과를 바탕으로, `PerspectivesService`의 cache key, TTL, 실패 처리, stale cache 허용 기준을 설명 가능하게 정리한다.
- Redis를 단순 성능 도구로만 쓰는 것이 아니라, 캐시 정합성 trade-off까지 문서화한다.

현재 확인한 정책:

- Redis key: `perspectives:article:{articleId}`
- TTL: `perspectives.cache-ttl-seconds`, 기본 3600초
- `perspectives.cache-ttl-seconds=0`이면 캐시 미사용
- Redis read 실패, JSON 역직렬화 실패는 warn 로그 후 DB/FULLTEXT 재계산
- Redis write 실패는 warn 로그 후 무시하고 API 응답은 반환
- 명시적 invalidation은 현재 없음

정합성 판단:

- 현재 서비스는 뉴스 수집 후 읽기 중심이며, 기사 제목/국가/category/source를 수정하는 API는 없다.
- `viewCount`, `summary`, `crawledContent` 변경은 Perspectives 응답에 포함되지 않아 무효화 대상이 아니다.
- 새 관련 기사 삽입, base article title 변경, country/category/source 변경은 stale cache 가능성이 있지만 현재는 1시간 TTL 기반 stale 허용으로 충분하다고 판단한다.
- 명시적 invalidation은 관리자 수정 API나 더 긴 TTL이 필요해질 때 별도 이슈로 검토한다.

---

### #131 - Perspectives API FULLTEXT 쿼리 EXPLAIN 분석

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/131
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/132
- 상태: merged
- 작업 브랜치: `perf/#131-perspectives-fulltext-explain`
- 주요 파일:
  - `docs/backend-improvement/perspectives-fulltext-explain.md`

목표:

- #127에서 cold cache 비용이 확인되었고 #129에서 Redis 정책을 정리했으므로, cache miss 경로의 DB FULLTEXT 쿼리가 의도한 인덱스를 사용하는지 MySQL `EXPLAIN`으로 확인한다.
- 캐시를 쓰는 이유뿐 아니라 캐시가 비었을 때 DB 쿼리가 어떤 실행 계획으로 동작하는지도 설명 가능하게 만든다.

조사 결과:

- 대상 쿼리: `ArticleRepository.findPerspectives`
- 대상 테이블: `article`
- 로컬 데이터 수: 9853 rows
- FULLTEXT 인덱스: `ft_article_title_description(title, description)`
- 대표 쿼리의 `EXPLAIN` 결과: `type=fulltext`, `key=ft_article_title_description`
- `ORDER BY published_at DESC` 때문에 `Using filesort`가 표시된다.
- `EXPLAIN ANALYZE` 기준 `+war` 404건 매칭은 정렬 포함 약 10.3ms, `+economy` 39건 매칭은 약 1.37ms였다.

판단:

- 현재 데이터 규모에서는 `findPerspectives` 쿼리가 FULLTEXT 인덱스를 사용하며, 즉시 쿼리/인덱스 변경이 필요한 병목으로 보이지 않는다.
- `Using filesort`는 매칭 결과가 커질 때 p95에 영향을 줄 수 있으므로 후속 관찰 포인트로 남긴다.
- 검색 품질, 다국어 매칭, semantic similarity는 별도 품질 개선 이슈로 분리한다.

---

### #133 - 검색 API FULLTEXT 성능 및 검색어별 안정성 분석

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/133
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/134
- 상태: merged
- 작업 브랜치: `perf/#133-search-fulltext-analysis`
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/domain/article/repository/ArticleRepository.java`
  - `src/main/java/com/example/globalTimes_be/domain/search/service/SearchArticlesService.java`
  - `docs/backend-improvement/search-fulltext-analysis.md`

목표:

- `/api/search`가 검색어별로 어떤 FULLTEXT 실행 계획을 사용하는지 확인한다.
- #123에서 수정한 영어 검색어 500 문제 이후, 영어/한국어/다국어 검색어별 매칭 수와 실행 계획 차이를 정리한다.

조사 결과:

- 검색 API는 원문 검색어와 번역 검색어를 `OR MATCH`로 묶어 검색한다.
- `war`, `economy`, `technology`, `대구` API 호출은 모두 200 응답을 반환했다.
- 로컬 DB 기준 FULLTEXT 매칭 수는 `war=404`, `technology=82`, `economy=39`, `대구 OR daegu=0`이었다.
- 단일 MATCH 쿼리는 `ft_article_title_description` FULLTEXT 인덱스를 사용했다.
- 원문과 번역어가 같은 영어 검색어에서도 기존 쿼리는 중복 `OR MATCH`가 되어 MySQL이 `idx_article_published_at` 역방향 스캔을 선택했다.

수정:

- `text`와 `translatedText`가 trim/case-insensitive 기준으로 같으면 단일 MATCH 쿼리를 사용하도록 분기했다.
- 원문과 번역어가 다르면 기존 OR MATCH 쿼리를 유지한다.
- API 응답 구조는 변경하지 않았다.

판단:

- 영어처럼 원문/번역어가 같은 검색어에서는 중복 OR를 제거해 의도한 FULLTEXT 인덱스를 사용하도록 하는 것이 작고 안전한 개선이다.
- 한국어/다국어 검색 품질, 형태소 분석, Elasticsearch/vector search는 별도 품질 개선 이슈로 분리한다.

검증:

```text
./gradlew.bat test
git diff --check
/api/search?text=war -> 200
/api/search?text=economy -> 200
/api/search?text=technology -> 200
/api/search?text=대구 -> 200
```

Reviewer 결과:

- Blocking 없음.
- Non-blocking으로 지적된 `EXPLAIN` 예시의 필터 조건 범위 설명은 같은 PR에서 보강했다.
- 2026-07-03 기준 PR #134는 merge 완료되었다.

---

### #137 - 기사 원문 크롤링 timeout 및 실패 처리 개선

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/137
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/138
- 상태: merged
- 작업 브랜치: `fix/#137-crawling-timeout-fallback`
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/global/crawler/ArticleCrawler.java`
  - `src/main/java/com/example/globalTimes_be/domain/detail/service/DetailService.java`
  - `src/main/java/com/example/globalTimes_be/domain/detail/service/ArticleCrawlContentService.java`
  - `src/main/java/com/example/globalTimes_be/domain/trend/service/TrendCrawledService.java`

목표:

- AI 요약/질의응답 경로에서 외부 언론사 페이지 크롤링이 사용자 요청을 오래 붙잡지 않도록 timeout을 명시한다.
- 크롤링 실패 또는 본문 추출 실패 시 API별 기존 fallback/에러 정책이 예측 가능하게 동작하도록 한다.
- DB 조회/저장 트랜잭션과 외부 네트워크 I/O를 분리한다.

조사 결과:

- `DetailService#getArticleCrawledContent()`는 `@Transactional(readOnly = true)` 안에서 외부 크롤링과 `articleRepository.save()`를 함께 수행하고 있었다.
- `DetailService`와 `TrendCrawledService` 모두 `Jsoup.connect(...).get()`에 timeout/user-agent 설정이 없었다.
- `AiController#summarizeArticle()`에는 크롤링 실패 시 기사 서두를 제공하는 fallback 분기가 있었지만, 기존 서비스가 예외를 던져 해당 분기가 사실상 도달하기 어려웠다.

수정 방향:

- `ArticleCrawler` 공통 컴포넌트로 Jsoup timeout/user-agent/본문 추출을 모은다.
- `DetailService`는 크롤링 실패 시 `null`을 반환해 일반 summary API의 기존 fallback 분기를 살린다.
- SSE summary와 ask API는 기존처럼 `DetailErrorStatus._CRAWLER_ERROR`를 유지한다.
- `ArticleCrawlContentService`를 별도 서비스로 분리해 DB 조회/저장 트랜잭션이 Spring 프록시를 타도록 한다.
- 비동기/Kafka는 이번 PR 범위에서 제외하고, 동기 요청 경로의 timeout/fallback을 먼저 안정화한다.

검증:

```text
./gradlew.bat test
git diff --check
```

Reviewer 결과:

- `## AI Reviewer 검토 결과` 제목의 Reviewer comment 기준 Blocking 없음.
- Non-blocking: `ArticleCrawler` URL 포함 warn 로그는 추후 운영 로그 정책에 따라 도메인/articleId 중심으로 축소 검토 가능.
- Non-blocking: PR #136 merge 후 Issue #135가 open으로 남은 housekeeping 항목 확인.
- 2026-07-03 기준 PR #138은 merge 완료되었고, Issue #137은 closed 상태다.

---

### #140 - Perspectives API 캐시/실패 회귀 테스트 기반 마련

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/140
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/141
- 상태: merged
- 작업 브랜치: `test/#140-perspectives-regression-tests`
- 주요 파일:
  - `src/main/java/com/example/globalTimes_be/domain/detail/service/PerspectivesService.java`
  - `src/test/java/com/example/globalTimes_be/domain/detail/service/PerspectivesServiceTest.java`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- #127/#129/#131에서 측정/문서화한 Perspectives cache hit/miss, Redis 실패, 번역 fallback 흐름을 테스트로 고정한다.
- 이후 Redis, 검색, 번역, 유사도 개선을 이어갈 때 기존 동작이 깨졌는지 자동으로 확인할 안전망을 만든다.

조사 결과:

- `PerspectivesService`는 cache hit 시 Redis JSON을 `PerspectivesResDTO`로 역직렬화해 즉시 반환한다.
- Redis read 실패 또는 캐시 역직렬화 실패는 warn 로그 후 재계산한다.
- cache miss 시 기준 기사 조회, 키워드 추출, 비영어 기사 번역, FULLTEXT 검색, 국가별 grouping, Redis 저장 순서로 동작한다.
- 번역 실패는 원문 키워드 fallback으로 처리한다.
- Redis write 실패는 warn 로그 후 API 응답을 정상 반환한다.

수정 방향:

- Spring context를 띄우지 않는 mock 기반 `PerspectivesServiceTest`를 추가한다.
- cache hit, cache miss, Redis read 실패, 캐시 역직렬화 실패, Redis write 실패, 번역 실패 fallback 분기를 테스트로 고정한다.
- API 응답 구조와 운영 코드는 변경하지 않고 회귀 테스트 안전망을 우선 확보한다.

검증:

```text
./gradlew.bat test
git diff --check
```

초기 테스트 실패와 조정:

- 캐시 JSON 테스트에서 `LocalDateTime` 직렬화를 위해 테스트 `ObjectMapper`에 JavaTime module 등록이 필요했다.
- 비영어 번역 실패 테스트는 `KeywordExtractor`의 실제 plain/boolean keyword 추출 결과를 기준으로 기대값을 맞췄다.
- 서비스 코드는 변경하지 않고 테스트 코드의 fixture/기대값만 실제 동작에 맞게 조정했다.
- 2026-07-03 기준 PR #141은 merge 완료되었고, Issue #140은 closed 상태다.

---

### #142 - Perspectives 다국어 이슈 매칭 품질 기준선 정의

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/142
- PR: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/143
- 상태: In Progress
- 작업 브랜치: `perf/#142-perspectives-matching-baseline`
- 주요 파일:
  - `docs/backend-improvement/perspectives-matching-quality-baseline.md`
  - `docs/backend-improvement/BACKLOG.md`
  - `docs/backend-improvement/WORK_PROGRESS.md`

목표:

- Perspectives API가 현재 제목 키워드, 영어 번역, MySQL FULLTEXT 검색으로 다국어 이슈를 어떻게 매칭하는지 기준선을 정의한다.
- Elasticsearch, Vector DB, RAG, issue clustering 같은 기술 도입 전에 대표 샘플 기준으로 현재 매칭 품질과 한계를 측정할 수 있게 만든다.
- #110의 장기 troubleshooting 내용은 직접 구현하지 않고, 그중 P3 다국어 이슈 매칭 품질 개선의 첫 측정 단계만 분리해 진행한다.

조사 결과:

- `PerspectivesService`는 기준 기사 제목에서 `KeywordExtractor.extractPlain()`과 `KeywordExtractor.extract()`로 평문/BOOLEAN MODE 키워드를 만든다.
- 기준 기사 언어가 영어가 아니면 평문 키워드를 영어로 번역한 뒤 다시 BOOLEAN MODE 키워드로 변환해 검색한다.
- 번역 키워드와 원문 키워드가 다르면 `findPerspectives`를 한 번 더 호출해 결과를 병합한다.
- `ArticleRepository.findPerspectives`는 `MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE)`와 `ORDER BY published_at DESC LIMIT 50`을 사용한다.
- 결과는 국가별로 그룹핑되고 국가당 최대 3개 기사로 제한된다.

수정 방향:

- API 동작, DB 스키마, 검색 엔진, 캐시 정책은 변경하지 않는다.
- 현재 매칭 흐름, 알려진 한계, 대표 샘플 선정 기준, 측정 절차, 결과 기록 템플릿을 문서화한다.
- 기술 도입 후보는 기준선 측정 이후 ADR 또는 후속 이슈로 분리한다.

검증:

```text
git diff --check
```

## 4. 이후 개선 로드맵

### A. #123 이후 바로 할 수 있는 성능 개선

#### 1. cold cache / warm cache 분리 측정

목표:

- Redis 캐시가 비어 있는 첫 요청과 캐시가 채워진 반복 요청의 p95 차이를 분리해 측정한다.

예상 이슈:

```text
[PERF] Perspectives API cold/warm cache 부하 테스트 분리
```

측정 예시:

| 시나리오 | p95 | 오류율 | 의미 |
| --- | --- | --- | --- |
| cold cache |  |  | 번역/FULLTEXT/캐시 저장 포함 |
| warm cache |  |  | Redis hit 중심 |

포트폴리오 문장 후보:

```text
Perspectives API의 cold/warm cache 시나리오를 분리 측정해 Redis 캐시 hit 시 p95 응답 시간이 얼마나 감소하는지 수치화했습니다.
```

#### 2. Perspectives Redis 캐시 개선

목표:

- 반복 요청에서 Redis 캐시 효과를 확인하고 TTL/key 정책을 점검한다.

예상 이슈:

```text
[PERF] Perspectives API Redis 캐시 hit/miss 성능 비교 및 개선
```

검토 후보:

- cache key 정책
- TTL 적정성
- 캐시 역직렬화 실패 시 재계산 정책
- 캐시 저장 실패 시 fallback

#### 3. 검색 API FULLTEXT 성능/안정성 개선

목표:

- 검색어별 응답 시간과 실패율을 측정한다.
- 영어/한국어/다국어 검색어의 동작 차이를 분석한다.

예상 이슈:

```text
[PERF] 검색 API FULLTEXT 성능 및 검색어별 안정성 개선
```

#### 4. 크롤링 안정성 개선

목표:

- AI 요약/질의응답에서 외부 언론사 페이지 크롤링이 사용자 요청에 직접 영향을 주는 문제를 완화한다.

예상 이슈:

```text
[FIX] 기사 원문 크롤링 timeout 및 실패 처리 개선
```

검토 후보:

- Jsoup timeout 설정
- 본문 없음 fallback
- 차단 응답 처리
- 재크롤링 방지
- 트랜잭션 내 외부 I/O 여부 점검

### B. AI Workflow 자동화 후속

#### 1. Reviewer comment 템플릿 강화

현재 `check-review-blocking.ps1`은 `## AI Reviewer 검토 결과` 제목을 기준으로 Reviewer comment를 찾는다.
앞으로는 Reviewer comment 템플릿을 더 엄격히 고정하면 오탐 가능성을 더 줄일 수 있다.

예상 이슈:

```text
[CHORE] AI Reviewer comment template 표준화
```

#### 2. MCP 서버화 후보

지금은 GitHub CLI와 PowerShell 스크립트로 다음을 처리한다.

- PR comment 조회
- 최신 AI Reviewer comment 식별
- Blocking 여부 판정

반복 패턴이 충분히 쌓이면 MCP tool로 확장할 수 있다.

후보 tool:

```text
get_latest_reviewer_comment(prNumber)
extract_blocking_items(prNumber)
get_review_decision(prNumber)
check_issue_scope(prNumber, issueNumber)
append_audit_log(...)
```

단, MCP는 지금 당장 만들기보다 실제 불편함과 반복 작업이 더 쌓인 뒤 진행한다.

### C. 장기 기술 후보와 도입 기준

초기 대화에서 Kafka, RAG, Vector DB, Elasticsearch, MCP 같은 기술 후보를 언급했지만, 현재 원칙은 “기술을 먼저 도입하지 않는다”이다.
포트폴리오에서 중요한 것은 기술 개수가 아니라, 어떤 문제를 측정했고 어떤 근거로 기술을 선택했는지다.

따라서 아래 기술들은 바로 TODO로 구현하지 않고, 문제와 지표가 확인되었을 때 ADR 또는 후속 이슈로 검토한다.

| 기술 후보 | 해결하려는 문제 | 도입 검토 조건 | 우선순위 |
| --- | --- | --- | --- |
| Redis cache 고도화 | 반복 조회, 번역 API 비용, Perspectives 응답 지연 | cold/warm cache p95 차이가 크고 cache hit 시 개선 폭이 확인될 때 | 높음 |
| Elasticsearch | MySQL FULLTEXT 한계, 다국어 검색 품질 부족 | 검색어별 누락/오류/응답 지연이 반복되고 MySQL FULLTEXT 개선으로 부족할 때 | 중간 |
| Vector DB / Embedding | 같은 사건의 표현 차이로 기사 매칭 누락 | 대표 이슈 샘플에서 국가별 관련 기사 매칭 품질 개선 필요가 확인될 때 | 중간 |
| RAG | 기사 요약/질의응답에서 원문 근거 추적과 답변 품질 개선 | AI 응답의 근거 추적, hallucination 방지, 문서/기사 검색 기반 답변이 필요할 때 | 중간 |
| Kafka / Message Queue | 뉴스 수집, 번역, 임베딩, 크롤링 작업의 비동기 처리 | 동기 요청 경로가 외부 API/크롤링 때문에 지연되거나 수집량 증가로 처리량 병목이 확인될 때 | 낮음-중간 |
| Rate Limit | 과다 트래픽에서 외부 API 비용/서버 보호 | k6 부하 테스트에서 특정 API 오류율 증가 또는 외부 API 호출 폭증이 확인될 때 | 중간 |
| MCP 서버 | AI 작업 흐름의 반복 조회/승인/로그 자동화 | PR comment 조회, Blocking 추출, 승인 상태 확인이 반복되어 CLI/스크립트만으로 불편해질 때 | 낮음-중간 |

#### Kafka를 바로 도입하지 않는 이유

Kafka는 대규모 이벤트 스트리밍과 높은 처리량이 필요한 경우 강력하지만, 현재 개인 프로젝트 단계에서는 운영 복잡도가 크다.
뉴스 수집/번역/임베딩/크롤링이 실제로 병목이 되고, 단순 스케줄러나 작업 큐로 부족하다는 근거가 생긴 뒤 검토한다.

후보 시나리오:

```text
RSS/News API 수집 이벤트
→ 중복 제거
→ 번역/정규화
→ 임베딩 생성
→ 검색 인덱스 갱신
```

#### RAG / Vector DB를 바로 도입하지 않는 이유

RAG와 Vector DB는 “같은 사건의 다른 국가 시선 연결”이라는 서비스 핵심과 잘 맞는다.
하지만 먼저 현재 FULLTEXT 기반 매칭의 실패 사례와 대표 이슈 샘플을 정의해야 한다.

후보 시나리오:

```text
대표 글로벌 이슈 샘플 선정
→ 현재 FULLTEXT 매칭 결과 기록
→ 누락 국가/오탐 기사 분석
→ 임베딩 기반 유사도 검색 PoC
→ 비용, 응답 시간, 정확도 비교
```

#### Elasticsearch를 검토할 수 있는 지점

MySQL FULLTEXT는 간단하지만 다국어 검색, 형태소 분석, ranking 제어에 한계가 있다.
검색 API의 실패율과 응답 시간, 검색 품질 문제가 반복적으로 확인되면 Elasticsearch 도입을 ADR로 검토한다.

#### MCP 서버를 검토할 수 있는 지점

현재는 GitHub CLI와 PowerShell 스크립트로 AI Reviewer comment 확인을 처리한다.
MCP는 다음 반복 작업이 많아졌을 때 검토한다.

```text
PR comment 조회
→ 최신 AI Reviewer comment 식별
→ Blocking 추출
→ 승인 상태 확인
→ 위험 변경 파일 감지
→ audit log 기록
```

이 흐름이 2~3개 이상의 실제 개선 PR에서 반복되면, MCP tool로 묶는 것이 자연스럽다.

## 5. 다음 세션에서 바로 이어가기 위한 시작 프롬프트

새 Codex 세션에서 이어갈 때 아래 내용을 전달하면 된다.

```text
GlobalTimes_BeSide 백엔드 개선 작업을 이어서 진행하려고 합니다.

먼저 docs/backend-improvement/WORK_PROGRESS.md 를 읽고 현재까지의 작업 흐름을 파악해줘.
우리는 Issue → Branch → 조사 → 계획 → 승인 → 구현 → 테스트 → PR → AI Reviewer comment → Blocking 확인 → merge 순서로 작업합니다.

현재까지 완료한 큰 흐름은 다음과 같습니다.
- #123/#126: 검색 API LazyInitializationException으로 인한 500 응답 수정
- #127/#128: Perspectives API cold/warm Redis cache 부하 테스트 분리
- #129/#130: Perspectives Redis cache 삭제/무효화/stale 허용 정책 문서화
- #131/#132: Perspectives FULLTEXT 쿼리 EXPLAIN 분석
- #133/#134: 검색 API FULLTEXT 성능 및 검색어별 안정성 분석, 중복 OR MATCH 최적화
- #137/#138: 기사 원문 크롤링 timeout/fallback 및 외부 I/O 트랜잭션 분리

바로 구현하지 말고,
1. 현재 develop 최신화
2. `WORK_PROGRESS.md`의 In Progress 작업과 최근 merged PR을 확인
3. 진행 중인 작업이 있으면 해당 Issue/PR/브랜치 상태를 확인
4. 진행 중인 작업이 없으면 이후 개선 로드맵에서 다음 후보를 제안
5. 관련 코드와 문서를 조사
6. 원인/범위/수정 계획을 제안
7. 사용자 승인 후 구현
순서로 진행해주세요.

다음 후보는 `WORK_PROGRESS.md`의 이후 개선 로드맵과 열린 GitHub Issue를 먼저 확인한 뒤 제안해줘.
현재 장기 열린 이슈로 #110 `[Troubleshooting] 서비스 설계의 근본적 한계`가 남아 있으므로, 이것이 바로 구현 이슈인지 아니면 별도 정리/문서화 이슈인지 먼저 판단해주세요.
```

## 6. 이 문서를 GitHub에 올릴지에 대한 판단

이 문서는 GitHub에 올리는 것을 추천한다.

이유:

- 작업 흐름과 의사결정이 PR/Issue 링크와 함께 남는다.
- context가 사라져도 새 세션에서 이어가기 쉽다.
- 포트폴리오 롱 자료를 작성할 때 근거 자료가 된다.
- AI를 단순 사용한 것이 아니라, 계획/승인/리뷰/검증 흐름으로 운영했다는 증거가 된다.

단, 아래 내용은 GitHub에 올리지 않는다.

- API 키, 토큰, 비밀번호
- `.env` 내용
- 원본 프롬프트 전문 중 민감 정보
- 외부 API 응답 전문
- 개인 정보 또는 사용자 데이터 원문

현재 문서는 민감 정보를 포함하지 않고, Issue/PR 링크와 작업 판단 중심이므로 GitHub에 올려도 된다.
