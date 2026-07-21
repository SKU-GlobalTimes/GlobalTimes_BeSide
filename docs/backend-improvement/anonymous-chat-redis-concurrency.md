# 익명 채팅 Redis 동시 저장 정합성 개선

- Issue: [#221](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/issues/221)
- PR: [#222](https://github.com/SKU-GlobalTimes/GlobalTimes_BeSide/pull/222)
- 상태: `Done`

## 문제

기존 익명 채팅은 `chat:anon:{sessionId}:{articleId}` String에 전체 대화 JSON을 저장했다. 각 요청이 기존 JSON을 읽고 Java에서 새 턴을 추가한 뒤 전체 값을 다시 `SET`하므로, 동일 세션의 SSE 응답이 겹치면 마지막 요청이 앞선 요청의 결과를 덮어쓸 수 있었다.

최근 대화 기사 인덱스도 `chat:anon:index:{sessionId}` JSON Map 전체를 같은 방식으로 갱신해 서로 다른 기사 요청 사이에서 항목이 유실될 수 있었다.

## 변경

| 데이터 | 기존 | 변경 |
| --- | --- | --- |
| 세션·기사별 대화 | String에 전체 JSON GET·수정·SET | List에 턴별 JSON `RPUSH`, 최근 N개 `LTRIM` |
| 세션별 최근 기사 | String에 전체 JSON Map GET·수정·SET | Sorted Set에 articleId와 활동 시각 `ZADD GT` |
| 만료 | String SET 시 TTL 지정 | List·Sorted Set 갱신 후 `EXPIRE` |
| key | `chat:anon:*` | 자료형 충돌을 피한 `chat:anon:v2:*` |

각 `RPUSH`와 `ZADD`는 Redis가 명령 단위로 처리하므로 기존처럼 전체 값을 읽고 덮어쓰지 않는다. 최근 활동 score는 `ZADD GT`로 기존 값보다 큰 시각만 반영해 지연된 요청이 최신 시각을 과거로 되돌리지 못하게 한다. Lua, `MULTI/EXEC`, 분산 락은 사용하지 않았다. 여러 명령 전체의 all-or-nothing 보장은 이번 범위가 아니며 실제 부분 실패 근거가 생기면 후속 검토한다.

## 검증 환경

- Java 17, Spring Boot 3.3.10, Spring Data Redis
- Docker Desktop, Redis `7.2-alpine` Testcontainer
- 동일 시작 latch를 사용한 동시 요청 20개
- 동시 대화 저장과 동시 기사 인덱스 시나리오를 각각 3회 반복
- 실제 Gemini·MySQL·HTTP 요청 없이 Redis 저장 경로만 격리 검증

## 결과

| 시나리오 | 기대 | 기존 JSON | List·Sorted Set | 반복 결과 |
| --- | ---: | ---: | ---: | --- |
| 동일 세션·기사에 동시 대화 20건 저장 | 20건 | 1건 보존·19건 유실 | 20건 보존·0건 유실 | 3/3회 동일 |
| 동일 세션의 서로 다른 기사 20건 인덱스 갱신 | 20건 | 전체 Map 덮어쓰기 위험 | 20건 보존·0건 유실 | 3/3회 동일 |
| 동일 기사의 score 200 저장 후 지연된 score 100 도착 | score 200 유지 | score 100으로 회귀 가능 | score 200 유지 | 통과 |
| 5건 순차 저장, `maxTurns=3` | 최근 3건 | 최근 3건 | 최근 3건 | 통과 |
| 테스트 TTL 60초 | 두 key 모두 TTL 존재 | TTL 존재 | `1~60초` 범위 | 통과 |

집중 테스트는 총 9개가 실행됐으며 failure·error·skip 없이 통과했다. 전체 Gradle 회귀 테스트는 67개 모두 통과했다.

## 해석 제한

- 기존 결과는 모든 worker가 같은 기존 값을 읽은 뒤 저장하도록 통제한 최악의 경쟁 조건이다. 실제 유실률을 의미하지 않는다.
- 이번 수치는 처리량·응답시간 개선이 아니라 동시 갱신 데이터 보존 결과다.
- 새 v2 key는 기존 String key와 자료형이 충돌하지 않는다. v1 key fallback은 제공하지 않으므로 전환 전에 생성된 익명 대화는 배포 직후부터 조회되지 않고 저장 공간만 최대 7일 TTL 후 자연 만료된다.
- 명령 묶음은 원자적으로 처리하지 않는다. `RPUSH` 후 `LTRIM` 실패 시 최대 턴을 초과하고, 대화 append 후 index 갱신 실패 시 최근 기사 목록에서 누락되며, `ZADD` 후 `EXPIRE` 실패 시 TTL이 설정되지 않을 수 있다.
- 로그인 사용자 MySQL 채팅, 실제 Gemini SSE, 다중 서버 Redis 장애 복구는 검증 범위가 아니다.
