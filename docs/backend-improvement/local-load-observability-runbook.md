# 로컬 부하 테스트 관측 runbook

## 한국어 학습 안내

이 문서는 부하 테스트 결과, 애플리케이션 단계별 log, Docker resource sample을 같은 run ID로 묶어 결과를 재현 가능하게 남기는 절차다. HTTP 상위 응답시간이 증가해도 DB query, Java 처리, 외부 대기, 로컬 CPU·memory 중 어디서 시간이 늘었는지 구분하지 못하면 잘못된 index나 기술을 선택할 수 있다.

핵심 개념은 **correlation ID 역할의 run ID**, **관측 시각 정렬**, **application metric과 system resource의 상관관계**, **측정 환경 기록**이다. 부하 도구 출력만 남기지 않고 app log와 resource snapshot을 같은 디렉터리에 저장해 한 실행의 근거를 묶는다.

이 runbook은 APM을 새로 도입하지 않고 기존 log, Actuator, Docker stats로 필요한 최소 신호를 수집한다. 반복 측정이 많아지고 장기 추세·분산 trace가 필요해질 때 별도 observability stack을 검토한다.

## 원본 실행 절차

## Purpose

This runbook defines how to capture local load-test evidence in the same run window:

- k6 p95, failure rate, request count, and RPS
- Spring Boot application latency logs such as `dbQueryMs` and `totalMs`
- local Docker resource signals for MySQL and Redis

The goal is to avoid jumping from "p95 increased" directly to "DB index needed".
For local tests, k6, Spring Boot, MySQL, Redis, IDE, and the OS share the same machine, so local CPU/memory or application thread pressure can look like API latency.

## 핵심 결과 요약

```text
로컬 부하 테스트에서 k6 p95/RPS, 애플리케이션 dbQueryMs 로그, Docker 리소스 지표를 같은 실행 구간에 매칭해 DB 병목과 실행 환경 한계를 구분할 수 있는 관측 절차를 정리했습니다.
```

Shorter resume keyword version:

```text
부하 테스트 로그/리소스 관측 기준 수립
```

## Scope Decision

This issue does not change:

- production code
- API response shape
- Repository queries
- DB schema or indexes
- Redis/cache policy
- k6 scripts
- Gemini or external API behavior

It only standardizes how local load-test evidence should be captured and interpreted.

## Why This Comes Before Gemini Mock Load Testing

Gemini mock latency testing is useful, but it needs reliable observation first.

Without this runbook, a slow AI question-answering test can be misread:

```text
mock latency 1s -> API p95 2s
```

Possible causes include:

- controlled mock latency
- Spring request thread pressure
- DB lookup or summary persistence delay
- connection pool pressure
- local CPU/memory saturation
- k6 and the application competing on the same laptop

Therefore the next AI mock test should reuse this runbook so that `AiSummary` logs, k6 output, and local resource signals are captured together.

## Run ID

Use a short run id for every local load test.

Format:

```text
YYYYMMDD-HHmm-<target>-<vus>
```

Examples:

```text
20260710-1430-popular-20vu
20260710-1440-popular-50vu
20260710-1450-ai-mock-1s-20vu
```

Use the same run id for:

- application log file name
- k6 output note
- resource snapshot note
- final documentation row

## Log Directory

Runtime logs must stay local and must not be committed.

Recommended local directory:

```text
.codex-tmp/load-runs/<RUN_ID>/
```

The repository already ignores `*.log`.
Do not add API keys, `.env` values, prompt text, user questions, full article text, or external API response bodies to committed docs.

## Application Log Capture

Preferred command for a local run:

```powershell
$runId = "20260710-1430-popular-20vu"
$runDir = ".codex-tmp/load-runs/$runId"
New-Item -ItemType Directory -Force $runDir
.\gradlew.bat bootRun --console=plain --args="--logging.file.name=$runDir/application.log"
```

If the terminal must stay available, use a separate PowerShell window for `bootRun`.
For repeatable evidence, prefer a visible terminal over hidden background process execution because Windows batch output redirection can be fragile.

Confirm the server is available:

```powershell
Test-NetConnection 127.0.0.1 -Port 8080
```

Expected log signals for article read APIs:

```text
[ArticlesPopular] page={} size={} from={} resultCount={} totalElements={} dbQueryMs={} totalMs={}
[ArticlesLatest] page={} size={} resultCount={} totalElements={} dbQueryMs={} totalMs={}
[ArticlesCursor] cursorProvided={} size={} resultCount={} hasNext={} dbQueryMs={} totalMs={}
[Explore] country={} category={} date={} cursorProvided={} size={} resultCount={} hasNext={} dbQueryMs={} totalMs={}
```

Expected log signals for AI summary/question-answering paths:

```text
[AiSummary] articleId={} summaryHit={} crawledContentRequested={} crawlerFallback={} aiRequested={} summaryLookupMs={} crawlContentMs={} fallbackContentMs={} aiSummaryMs={} summarySaveMs={} totalMs={}
[ArticleCrawlContent] articleId={} crawledContentHit={} crawlAttempted={} crawlSuccess={} crawlTargetLookupMs={} crawlMs={} saveMs={} totalMs={}
```

## k6 Capture

Run k6 in a second terminal and record the start/end time manually in the run note.

Example popular-focused run:

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:ARTICLES_SIZE = "20"
$env:LATEST_VUS = "1"
$env:CURSOR_VUS = "1"
$env:POPULAR_VUS = "20"
$env:EXPLORE_VUS = "1"
$env:DETAIL_VUS = "1"
$env:LATEST_DURATION = "1s"
$env:CURSOR_DURATION = "1s"
$env:POPULAR_DURATION = "60s"
$env:EXPLORE_DURATION = "1s"
$env:DETAIL_DURATION = "1s"
$env:POPULAR_PAGES = "0,1,5"
$env:SLEEP_SECONDS = "0.1"
k6 run .\load-tests\k6\articles-read-high-load.js
```

Record at least:

- target endpoint
- VUs
- duration
- total requests
- RPS
- p95
- failure rate

## Resource Capture

For Docker containers:

```powershell
docker stats --no-stream
```

Record:

- MySQL CPU %
- MySQL memory usage
- Redis CPU %
- Redis memory usage
- whether either container looked saturated during the run

For local machine signals, use Windows Task Manager or Resource Monitor and record short notes:

- CPU was normal / high / saturated
- memory was normal / high / saturated
- disk looked normal / busy
- system responsiveness was normal / laggy

Do not over-interpret local resource numbers as production capacity.
They are only local evidence to separate DB query cost from laptop saturation.

## Result Template

| Run ID | Target | VUs | Duration | Requests | RPS | p95 | Failure rate | App log signal | Resource signal | Interpretation |
| --- | --- | ---: | --- | ---: | ---: | ---: | ---: | --- | --- | --- |
|  | popular |  |  |  |  |  |  | `[ArticlesPopular] dbQueryMs=?, totalMs=?` | MySQL CPU/MEM, local CPU/MEM |  |

## Interpretation Rules

| k6 p95 | App `dbQueryMs` | App `totalMs` | Resource signal | Likely next step |
| --- | --- | --- | --- | --- |
| high | high | high | normal | DB query/index investigation |
| high | low | high | normal | application processing, serialization, thread pool, or external call investigation |
| high | low or mixed | high | local CPU/memory high | local environment saturation; reduce VUs or move test to isolated environment |
| high | high | high | MySQL CPU/memory high | DB load investigation, connection pool review, or query/index candidate |
| normal | low | low | normal | no immediate performance change |

## #176 Popular Result Reinterpretation

#176 found:

| Popular VUs | RPS | Popular p95 | Failure rate |
| ---: | ---: | ---: | ---: |
| 20 | 106.29/s | 134.87ms | 0.00% |
| 50 | 114.33/s | 423.79ms | 0.00% |
| 100 | 123.61/s | 902.51ms | 0.00% |

MySQL `EXPLAIN ANALYZE` remained low:

- page 0 content query: about `6.44ms`
- page 5 content query: about `11.5ms`
- count query: about `0.696ms`

Interpretation:

- p95 rose under VU pressure.
- RPS plateaued rather than scaling with VUs.
- DB-side single-query actual time stayed low.
- The next run must capture `[ArticlesPopular] dbQueryMs`, `totalMs`, and resource signals before opening an index implementation issue.

## Gemini Mock Follow-up

After this runbook is available, the next external-call performance issue can use a mock AI server.

Candidate:

```text
[PERF] AI 질의응답 Gemini 외부 호출 mock latency 부하 테스트
```

Recommended controlled conditions:

- mock latency 200ms
- mock latency 1s
- mock latency 3s
- timeout or 5xx fallback

Required evidence:

- k6 p95/failure rate
- `AiSummary` or question-answering service `totalMs`
- external/mock call latency field if available
- local resource signal
- decision on whether synchronous external call, timeout policy, fallback, cache, or async processing should be considered next
