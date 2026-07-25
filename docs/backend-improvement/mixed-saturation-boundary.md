# 혼합 API 단일 instance 포화 경계

## 한국어 학습 안내

이 문서는 안정적으로 처리된 mixed arrival-rate 기준선과 동일한 endpoint 비율·mock 지연·실행시간을 유지하면서 요청 도착률만 높여 최초 포화 신호를 찾는다. 여러 조건을 동시에 바꾸지 않아야 latency 증가를 arrival 변화와 연결할 수 있다.

핵심 개념은 **포화 경계**, **재현성**, **connection pool pending**, **thread 증가**, **자원 상관분석**이다. 목표 RPS 달성 여부만으로 안정성을 판단하지 않고 endpoint별 상위 응답시간, Hikari active/pending, Tomcat busy/current, executor active/queued와 CPU·memory를 함께 본다.

첫 실행과 반복 실행이 다르면 로컬 thermal·background process·warm-up 영향을 고려해야 한다. 이 문서의 경계는 제한된 노트북 환경에서 후속 고정 자원 container 측정이 필요하다는 근거이며, 운영 capacity나 scale-out 효율을 직접 증명하지 않는다.

## 원본 측정 기록

## Status

Issue #194 is complete in PR #195.

## Purpose

#192 established a stable synthetic mixed-traffic baseline through 60 RPS. This follow-up keeps the same endpoint ratio, local dependencies, mock latency, and 30-second window while increasing arrivals to `80 -> 100 -> 120 RPS`.

The question is no longer whether a known load is stable. It is where achieved throughput stops following scheduled arrivals and which resource signal changes first.

## Scope and guardrail

Included:

- reuse `load-tests/k6/mixed-arrival-rate.js`
- preserve the `35/25/20/15/5` endpoint ratio
- correlate endpoint latency and achieved RPS with Tomcat, Hikari, AI executor, application memory, and Docker MySQL/Redis samples
- stop escalation as soon as a saturation or unsafe-host signal is clear

Excluded:

- production code, query, index, cache, Hikari, Tomcat, or executor tuning
- real Google Translate or Gemini traffic
- application containerization, fixed cgroup limits, distributed k6, EC2, or Kubernetes
- production SLA or multi-Pod capacity claims

Extending the same experiment is simpler and more comparable than introducing a new infrastructure variable. If 120 RPS is still stable, the next step is a fixed-resource environment rather than another issue that only raises the number.

## Arrival stages

| Endpoint | Ratio | 80 RPS | 100 RPS | 120 RPS |
| --- | ---: | ---: | ---: | ---: |
| latest | 35% | 28 | 35 | 42 |
| popular | 25% | 20 | 25 | 30 |
| detail | 20% | 16 | 20 | 24 |
| search | 15% | 12 | 15 | 18 |
| mock summary | 5% | 4 | 5 | 6 |

With a fixed three-second mock latency, summary requires approximately 12, 15, and 18 concurrent workers. The highest stage remains below the default 20-worker executor capacity so DB-backed traffic can rise without intentionally making the AI executor the first bottleneck.

## Stop conditions

Do not continue to the next stage when one of these signals is clear:

- achieved business RPS no longer follows scheduled RPS
- `dropped_iterations` becomes nonzero
- read failure or unexpected summary status occurs
- endpoint p95 rises sharply without throughput gain
- Hikari pending, Tomcat busy, or executor queue reaches a sustained limit
- the laptop becomes unresponsive or memory pressure is unsafe

An isolated periodic CPU sample is not enough to declare a DB bottleneck. It must be interpreted with latency, throughput, Hikari pending, and application logs.

## Controlled fixture

Use the same article and mock path as #192. Back up and restore:

- article summary
- article view count
- any pre-existing Redis value and TTL for `translation:war`

Set `GEMINI_API_KEY=dummy` in addition to overriding the base URL. Before load, verify one summary call takes about three seconds and one search call logs or behaves as a Redis cache hit.

Runtime artifacts and fixture backups stay under `.codex-tmp/` or temporary local DB state and must not be committed.

## Execution

```powershell
$env:BASE_URL = "http://localhost:8080"
$env:ARTICLE_ID = "7366"
$env:SEARCH_TEXT = "war"
$env:DURATION = "30s"
$env:METRICS_INTERVAL_SECONDS = "5"

$env:TOTAL_RPS = "80"
k6 run .\load-tests\k6\mixed-arrival-rate.js

$env:TOTAL_RPS = "100"
k6 run .\load-tests\k6\mixed-arrival-rate.js

$env:TOTAL_RPS = "120"
k6 run .\load-tests\k6\mixed-arrival-rate.js
```

## Results

| Target RPS | Achieved business RPS | Dropped | Latest p95 | Popular p95 | Detail p95 | Search p95 | Summary 200/503 | Summary p95 | Tomcat busy/current peak | Hikari active/pending peak | Executor active/queued peak |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 80 repeat | 80.13 | 0 | 59.35ms | 95.27ms | 67.08ms | 106.62ms | 121/0 | 3.08s | 5/11 | 5/0 | 12/0 |
| 100 | 100.17 | 0 | 112.78ms | 156.36ms | 122.60ms | 174.99ms | 151/0 | 3.13s | 21/21 | 10/11 | 16/0 |
| 120 | 120.07 | 0 | 247.61ms | 287.85ms | 254.69ms | 308.08ms | 180/0 | 3.35s | 27/40 | 10/18 | 20/0 |

`Achieved business RPS` is the sum of the five business endpoint counters divided by the 30-second arrival window. It excludes Actuator telemetry and the graceful completion period.

### First 80 RPS attempt and repeatability

The first 80 RPS attempt recorded seven dropped iterations, Hikari active/pending `10/19`, read p95 between 383ms and 456ms, and a MySQL CPU periodic sample of 118.52%. After all pools returned to zero and MySQL CPU returned near idle, the same 80 RPS stage was repeated.

The repeat achieved 80.13 business RPS with zero drops, Hikari pending zero, and read p95 at or below 106.62ms. The first result is retained as a local warm-up/host-variance warning, but it is not used alone as the saturation boundary.

### Resource samples

These are the highest periodic samples observed during each comparison run, not continuous profiler peaks.

| Target RPS | App working set | MySQL CPU sample | MySQL memory | Redis memory |
| ---: | ---: | ---: | ---: | ---: |
| 80 repeat | 484.1MiB | 100.65% | 461.8MiB | 11.70MiB |
| 100 | 486.6MiB | 152.81% | 462.4MiB | 11.60MiB |
| 120 | 489.4MiB | 203.38% | 463.6MiB | 12.12MiB |

### Interpretation

- Scheduled throughput was maintained through 120 RPS with zero failure, zero summary 503, and zero dropped iterations in the comparison runs. A throughput saturation boundary was not reached.
- MySQL CPU samples rose from about one core at 80 RPS to about two cores at 120 RPS, while Hikari pending rose from 0 to 11 and 18 at 100 and 120 RPS.
- The maximum read p95 rose from 106.62ms to 174.99ms and 308.08ms. This is increasing DB-side contention/connection-wait pressure, even though throughput still scaled.
- Tomcat created additional request threads rather than reaching a fixed maximum. Executor active tasks followed the expected summary concurrency and reached 20 at 120 RPS without queueing or rejection.
- Application, MySQL, and Redis memory remained stable. The observed pressure is CPU/connection concurrency rather than memory exhaustion.
- The practical conclusion is `stable throughput through 120 RPS, with DB resource pressure becoming visible from 100 RPS`. It is not valid to claim that 120 RPS is the production capacity limit.

### Follow-up decision

Do not immediately add an index or enlarge Hikari. The mixed workload contains latest, popular, detail writes, and FULLTEXT search, so aggregate CPU and pending connections do not identify one query.

The next useful issue is a fixed-resource, endpoint-attributed DB investigation: keep a reproducible CPU/memory envelope and use per-endpoint application/DB timing or MySQL statement evidence to identify which query consumes the pressure. Raising mixed RPS beyond 120 on the same unconstrained laptop is not the next step.

## Decision rule

- DB candidate: MySQL utilization and Hikari pending or DB/application latency rise with client p95.
- Servlet candidate: Tomcat busy approaches current/max while Hikari pending and DB time remain low.
- AI candidate: executor active/queued reaches its bound and summary 503 appears while read APIs remain protected.
- Host candidate: application/client latency rises with local CPU or memory pressure but internal DB/thread metrics do not explain it.

Only a repeated first bottleneck signal can open a tuning issue.
