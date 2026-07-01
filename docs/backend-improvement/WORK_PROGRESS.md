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

### #123 - 검색 API LazyInitializationException으로 인한 500 응답 수정

- Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/123
- 상태: 시작 전

문제:

```text
/api/search?text=war
/api/search?text=korea
/api/search?text=economy
/api/search?text=technology
```

위 요청에서 500 응답이 발생했다.
서버 로그에는 `LazyInitializationException`이 기록되었다.

확인된 로그 요약:

```text
[TranslateUtil] success textLength=10 translatedLength=10
[Translation] cacheHit=false externalCall=true externalCallMs=116 elapsedMs=117 textLength=10 translatedLength=10
GlobalErrorHandler : 발생한 예외 타입: LazyInitializationException
GlobalErrorHandler : Exception Error
```

현재 추정 원인:

- `SearchArticlesService`에서 검색 결과 `Article`을 DTO로 변환할 때 `article.getSource().getSourceName()`에 접근한다.
- `Article.source`가 lazy loading 관계라면, Repository 조회 이후 영속성 컨텍스트가 닫힌 상태에서 source에 접근하며 `LazyInitializationException`이 발생할 수 있다.
- 한국어 검색어 `대구`는 200이었고 영어 검색어에서 500이 발생했으므로, 검색 결과 데이터 또는 FULLTEXT 결과에 source lazy loading 문제가 있는 article이 포함될 가능성이 있다.

관련 코드 후보:

- `src/main/java/com/example/globalTimes_be/domain/search/controller/SearchController.java`
- `src/main/java/com/example/globalTimes_be/domain/search/service/SearchArticlesService.java`
- `src/main/java/com/example/globalTimes_be/domain/article/repository/ArticleRepository.java`
- `src/main/java/com/example/globalTimes_be/domain/article/entity/Article.java`
- `src/main/java/com/example/globalTimes_be/domain/source/entity/Source.java`
- `src/main/java/com/example/globalTimes_be/domain/search/dto/response/SearchArticleDTO.java`

우선 조사할 것:

1. `/api/search?text=war` 500 재현
2. stack trace 전체 확인
3. `Article.source` 연관관계 fetch type 확인
4. `SearchArticlesService.getSearchArticles()`에 `@Transactional(readOnly = true)`가 없는지 확인
5. Repository native query 결과에서 source 로딩 방식 확인
6. N+1 가능성 확인

수정 후보:

| 방법 | 장점 | 단점 |
| --- | --- | --- |
| `SearchArticlesService.getSearchArticles()`에 `@Transactional(readOnly = true)` 추가 | 변경이 작고 LazyInitializationException 해결 가능성이 높음 | source 접근 시 N+1 가능성은 남을 수 있음 |
| Repository 검색 쿼리에 fetch join 또는 EntityGraph 적용 | source를 함께 로딩해 안정적 | native query와 조합이 제한될 수 있음 |
| DTO projection으로 필요한 필드만 조회 | 성능과 안정성 면에서 명확 | 변경 범위가 커질 수 있음 |

추천 접근:

1. 먼저 가장 작은 수정으로 `@Transactional(readOnly = true)` 적용 가능성을 검토한다.
2. 해결되면 k6 smoke test를 재실행해 오류율 개선을 확인한다.
3. N+1이나 추가 성능 문제가 보이면 후속 이슈로 fetch/projection 개선을 분리한다.

검증 계획:

```powershell
Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/api/search?text=war"
Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/api/search?text=korea"
Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/api/search?text=economy"
Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/api/search?text=technology"
```

이후 k6 smoke test:

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:ARTICLE_ID = "8449"
$env:SEARCH_TEXT = "war"
$env:ARTICLES_VUS = "1"
$env:SEARCH_VUS = "1"
$env:PERSPECTIVES_VUS = "1"
$env:ARTICLES_DURATION = "15s"
$env:SEARCH_DURATION = "15s"
$env:PERSPECTIVES_DURATION = "15s"
& "C:\Program Files\k6\k6.exe" run .\load-tests\k6\api-baseline.js
```

기대 포트폴리오 수치:

```text
검색 API 영어 검색어 요청에서 LazyInitializationException으로 500 응답 발생
→ 부하 테스트 smoke run 기준 실패율 16.25%
→ Lazy loading 문제 수정 후 실패율 0.00%로 개선
```

`16.25%`는 #121 k6 smoke test 중 `SEARCH_TEXT=war`, `ARTICLE_ID=8449`, 각 시나리오 VU 1명, duration 15초 조건에서 측정된 값이다.
당시 총 80 requests 중 13건이 2xx 응답을 받지 못해 `http_req_failed=16.25%`로 기록되었다.

주의:

- #123은 실제 API 버그 수정 PR이다.
- #122와 섞지 않는다.
- 수정 전 반드시 원인 조사와 계획을 먼저 제시하고 승인받는다.

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

다음 작업은 #123 입니다.
Issue: https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/123
목표: /api/search 영어 검색어 요청에서 발생하는 LazyInitializationException 500 응답 수정

바로 구현하지 말고,
1. 현재 develop 최신화
2. #123 작업 브랜치 생성
3. 관련 코드 조사
4. 원인 분석
5. 수정 계획 제안
6. 사용자 승인 후 구현
순서로 진행해주세요.
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
