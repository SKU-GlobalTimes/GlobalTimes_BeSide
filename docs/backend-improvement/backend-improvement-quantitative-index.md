# 백엔드 개선 정량 결과 인덱스

- Issue: [#218](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/218)
- 상태: `Done` ([PR #220](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/220))

## 목적

이 문서는 GlobalTimes 백엔드 개선 과정에서 확인한 정량 결과를 빠르게 추적하기 위한 인덱스다. 상세 수치와 재현 절차는 각 PR과 측정 문서가 원본이며, 여기서는 다음 연결만 압축한다.

```text
도메인 문제 → 적용한 개선 → 정량 결과 → 검증 방법 → 원본 근거
```

도구 자체는 성과가 아니다. k6는 HTTP 부하와 응답을, mock server는 외부 의존성 조건을, Testcontainers는 실제 MySQL 동작을 통제해 개선 결과를 검증하는 수단이다.

## 검증 도구의 역할

| 도구 | 확인한 것 | 확인하지 못하는 것 |
| --- | --- | --- |
| k6 | VU 또는 arrival-rate 기반 HTTP RPS, p95, 실패율, 동시 요청 결과 | 운영 SLA, 실제 사용자 트래픽 분포 |
| local mock server | Gemini·News API의 delay, 5xx, timeout을 비용·quota 없이 반복 | 실제 외부 사업자의 capacity와 품질 |
| MySQL Testcontainers | MySQL query, transaction, constraint, migration, 동시성 회귀 | 운영 DB의 데이터 분포와 장기 부하 |
| Hibernate Statistics | 한 service 호출의 SQL statements와 entity loads | DB CPU와 운영 네트워크 비용 |
| EXPLAIN ANALYZE | DB 내부 실행 계획과 actual time | HTTP 전체 응답시간 |
| Actuator·application log | Tomcat thread, Hikari connection, async queue, 단계별 latency | 연속 profiler가 없는 구간의 절대 peak |

## 실제 개선 전후

| 도메인 문제 | 개선 | 정량 결과 | 검증 | 근거 |
| --- | --- | --- | --- | --- |
| Gemini 지연 시 90초 고정 대기와 upstream 오류의 backend 500 혼합 | timeout을 설정형 10초로 낮추고 upstream 502·timeout 504·내부 500 분리 | 15초 mock 조건 p95 `16.08초 → 10.42초`, mock 500은 약 0.31초에 502 | k6 + Gemini mock | [PR #185](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/185), [상세](gemini-timeout-upstream-error-policy.md) |
| 동기 Gemini 호출이 Servlet thread를 점유해 동시 조회 API까지 지연 | `WebAsyncTask`와 bounded executor로 외부 호출 worker 격리 | 50 VU에서 popular p95 `5.97초 → 172.98ms`(약 97.1% 감소), RPS `0.91 → 22.40`; summary 처리량은 약 6.29 RPS로 동일 | k6 + Gemini mock + Actuator | [PR #189](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/189), [상세](gemini-servlet-async-comparison.md) |
| executor 초과 요청이 무제한 대기하면 queue와 서버가 함께 악화될 위험 | bounded queue 포화 시 HTTP 503 fast rejection과 회복 경로 검증 | 5 workers/queue 10에서 20 VU부터 503, queue peak 10 유지, rejected p95 `34~50ms`, 부하 후 25/25 HTTP 200 회복 | k6 + Gemini mock + Actuator | [PR #191](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/191), [상세](gemini-executor-overload-protection.md) |
| 기사 조회수 read-modify-write의 lost update | DB 원자 `view_count = view_count + 1` UPDATE | 20개 성공 요청에서 DB 증가량 `2 → 20`, 유실 `18 → 0` | k6 + MySQL Testcontainers | [PR #199](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/199), [PR #203](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/203), [상세](atomic-view-count-consistency.md) |
| 채팅 팝업이 전체 이력 5,000건을 적재하고 Article N+1 수행 | MySQL `ROW_NUMBER()` latest-per-article projection | SQL `101 → 1`, entity loads `5,100 → 0`, service elapsed 실행별 `83.0~86.8%` 감소 | Testcontainers + Hibernate Statistics + EXPLAIN | [PR #215](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/215), [상세](chat-history-latest-query.md) |
| 로그인 스크랩의 2단계 N+1과 비로그인 ID별 반복 조회 | DTO projection 일괄 조회와 요청 ID 순서 복원 | 로그인 SQL `201 → 1`·entity `300 → 0`, 비로그인 SQL `200 → 1`·entity `200 → 0` | Testcontainers + Hibernate Statistics + EXPLAIN | [PR #217](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/217), [상세](scrap-list-query-optimization.md) |
| 수집 로직만으로는 과거·동시 기사 URL 중복을 최종 차단할 수 없음 | SHA-256 generated column UNIQUE와 안전한 Flyway 중복 정리 | 기사 `9,853 → 9,814`, 초과 중복 `39 → 0`; 동일 URL 동시 INSERT 1건 성공·1건 거부 | Testcontainers + Flyway + local MySQL | [PR #211](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/211), [상세](article-url-uniqueness.md) |

## 측정으로 확인한 효과와 기준선

아래 항목은 유용한 정량 근거지만 모두 해당 PR에서 새로운 성능 구현을 추가한 것은 아니다. “개선했다”보다 “효과 또는 경계를 측정했다”로 표현한다.

| 대상 | 시나리오와 결과 | 결론 | 근거 |
| --- | --- | --- | --- |
| Perspectives Redis cache | 동일 article의 cold p95 `218.65ms`, warm p95 `21.29ms` | cache hit 경로가 약 90.3% 짧았으나 cold는 1회 표본 | [PR #128](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/128), [상세](perspectives-cache-load-test.md) |
| 기사 popular 단일 인스턴스 | 20 VU 약 `87.29 RPS`, p95 `105.48ms`; 50 VU `86.15 RPS`, p95 `456.72ms` | 처리량 증가 없이 latency가 상승한 로컬 포화 신호 | [PR #181](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/181), [상세](single-instance-tps-baseline.md) |
| mixed API 안정 구간 | 20/40/60 target에서 achieved `20.13/40.17/60.13 RPS`, dropped·failure 0, read p95 최대 `81.59ms` | 60 RPS까지 첫 병목 없음 | [PR #193](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/193), [상세](mixed-arrival-rate-baseline.md) |
| mixed API 자원 압박 | 120 target에서 `120.07 RPS`, dropped·failure 0, read p95 최대 `308.08ms`, Hikari pending 18 | throughput 한계가 아니라 100 RPS부터 나타난 DB CPU·connection pressure | [PR #195](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/195), [상세](mixed-saturation-boundary.md) |
| Gemini 외부 지연 | mock 200ms/1s/3s에서 p95 약 `263~290ms/1.04~1.06초/3.04~3.05초` | 동기 외부 호출 latency가 사용자 응답을 지배 | [PR #183](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/183), [상세](gemini-mock-latency-baseline.md) |
| Servlet thread 전파 | summary 20 VU에서 busy `20/20`, popular p95 `154.46ms → 2.86초`, RPS `24.55 → 3.71` | DB보다 request-thread 대기가 다른 API로 전파 | [PR #187](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/187), [상세](gemini-servlet-thread-saturation-baseline.md) |
| 검색 FULLTEXT | 검색어별 p95 `24.78~103.41ms`, failure 0%; 한국어·AI 샘플 결과 0건 | latency보다 다국어 recall이 후속 품질 문제 | [PR #169](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/169), [상세](search-fulltext-term-baseline.md) |
| 기사 summary hit | p95 `332.6ms`, failure 0%, crawler fallback 0%, summary success 100% | 저장 summary가 외부 crawl·AI를 우회하는 warm 기준선 | [PR #171](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/171), [상세](article-crawl-latency-baseline.md) |

## 장애 격리와 재현성

| 문제 | 조치와 결과 | 검증 | 근거 |
| --- | --- | --- | --- |
| 실제 News API로 500·timeout을 반복 재현하기 어려움 | random-port mock upstream의 200·500·timeout 혼합에서 정상 기사 2건 저장, 실패 category 격리, 재실행 후 URL 중복 0건 | mock HTTP + Testcontainers | [PR #213](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/213), [상세](news-api-ingestion-failure-e2e.md) |
| DB별 schema 차이와 수동 회귀 위험 | Flyway V1~V3와 Hibernate validate, MySQL Testcontainers를 CI에서 반복 | 전체 schema·FK·FULLTEXT·URL UNIQUE 재현 | [PR #209](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/209), [PR #207](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/207), [상세](flyway-schema-baseline.md) |

## 핵심 개선 결과 요약

아래 문장은 각 개선 결과를 문제, 조치, 결과 중심으로 압축한 요약이다.

```text
- Gemini 동기 호출의 Servlet thread 점유를 bounded async executor로 격리해 동시 popular API p95를 5.97초에서 173ms로 단축하고 처리량을 0.91에서 22.40 RPS로 회복
- 기사 조회수 동시 갱신의 lost update를 DB 원자 UPDATE로 개선해 20개 성공 요청의 실제 증가량을 2건에서 20건으로 일치
- 채팅·스크랩 목록의 전체 entity 로딩과 N+1을 projection query로 개선해 SQL을 각각 101→1, 201→1로 감소
- 로컬 Docker 단일 인스턴스의 mixed API arrival-rate를 20→120 RPS로 높여 100 RPS부터 Hikari pending과 MySQL CPU 압박이 증가하는 첫 병목 신호 식별
- mock upstream과 MySQL Testcontainers로 News API 500·timeout 부분 실패를 재현하고 정상 기사 저장 및 재실행 URL 중복 0건을 자동 검증
```

Redis cache는 직접 구현 범위를 확인한 뒤 다음처럼 보수적으로 사용한다.

```text
- Perspectives API의 Redis cold/warm 경로를 분리 측정해 cache hit p95가 218.65ms에서 21.29ms로 감소하는 효과 검증
```

## 검증 결과 해석 순서

1. 실제 코드 또는 반복 가능한 시나리오에서 문제를 재현했다.
2. k6 p95만 보고 결론 내리지 않고 application log, Actuator, EXPLAIN, Hibernate Statistics를 함께 확인했다.
3. 병목과 직접 관련된 작은 변경만 적용했다.
4. 같은 조건에서 개선 전후 결과와 회귀 동작을 비교했다.
5. 로컬·mock·합성 fixture의 한계를 밝히고 운영 수치로 일반화하지 않았다.

## 신규 기술 도입 기준

| 기술 | 현재 보류 이유 | 도입 검토 신호 |
| --- | --- | --- |
| Kafka·Message Queue | 단일 순차 수집과 bounded executor로 현재 실패 격리 가능 | 수집 backlog, 재처리 요구, producer와 consumer 처리량 분리가 반복 측정될 때 |
| Vector DB·Embedding | 현재 FULLTEXT의 실패 샘플과 기대 정답 집합이 충분하지 않음 | 같은 사건 표현 차이로 국가별 기사 누락이 대표 샘플에서 반복되고 accuracy 개선을 비교할 수 있을 때 |
| Elasticsearch | 현재 검색 latency는 낮고 주요 문제는 다국어 결과 누락 | 형태소·다국어 recall 실패가 반복되고 MySQL FULLTEXT로 해결하기 어려울 때 |
| 추가 Redis cache | 실제 데이터가 작고 무효화 비용이 생김 | 동일 read의 cold/warm p95 차이와 hit rate가 반복 측정되고 정합성 정책을 정의할 수 있을 때 |
| 다중 Pod·분산 부하 | 현재 결과는 8GB 로컬 단일 인스턴스 | 고정 CPU·memory 인스턴스의 단일 capacity를 재현한 뒤 scale-out 효율을 직접 비교할 때 |

## 표현 제한

- VU는 동시 사용자 수이며 RPS/TPS가 아니다.
- 이 프로젝트의 RPS는 HTTP request 한 건을 한 transaction으로 본 문맥에서만 TPS와 비슷하게 사용한다.
- 120 RPS는 확인한 최대 단계이지 운영 최대 처리량이나 한계 TPS가 아니다.
- mock Gemini 결과는 이 백엔드가 통제된 외부 지연에 반응한 결과이며 실제 Gemini 성능이 아니다.
- Testcontainers fixture는 구조적 개선을 재현하는 데이터이며 운영 데이터가 아니다.
- 빠른 503은 overload protection이며 처리량 향상이 아니다.
