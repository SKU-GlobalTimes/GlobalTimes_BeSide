# 스크랩 토글 동시 요청 정합성 개선

## 문제

로그인 사용자의 스크랩 API는 `POST /api/articles/{id}/scrap` 한 번으로 현재 상태를 반전한다.

기존 `ScrapService.toggle()`은 하나의 transaction에서 다음 순서로 동작했다.

```text
user 조회
article 조회
scrap(user_id, article_id) 조회
있으면 DELETE, 없으면 INSERT
commit
```

`scrap(user_id, article_id)`에는 unique 제약이 있어 중복 행은 저장되지 않는다. 그러나 scrap 행이 아직 없을 때 동일 사용자·동일 기사 요청이 겹치면 여러 transaction이 모두 “없음”을 읽은 뒤 INSERT를 시도할 수 있다. unique 제약은 데이터 중복을 막지만 충돌한 API 요청의 성공까지 보장하지 않는다.

## 측정 환경

| 항목 | 값 |
| --- | --- |
| DB | MySQL 8 Testcontainers |
| 대상 | 동일 user ID, 동일 article ID |
| 초기 상태 | scrap 0건 |
| 동시 요청 | 20 |
| 시작 제어 | `CountDownLatch` |
| transaction | 실제 `ScrapService.toggle()` 호출 |

## 개선 전

동시 요청 20건을 같은 시점에 시작한 결과는 다음과 같았다.

| 지표 | 결과 |
| --- | ---: |
| 성공 | 2 |
| 실패 | 18 |
| `scrapped=true` | 1 |
| `scrapped=false` | 1 |
| 최종 scrap 행 | 0 |

실패 요청은 같은 unique key에 대한 INSERT 경쟁에서 발생했다. 공통 예외 처리에는 해당 충돌을 별도 처리하는 경로가 없어 HTTP 요청에서는 500으로 변환될 수 있다.

최종 행이 0건이라는 사실만으로 정상이라고 볼 수 없다. 순차 toggle 두 번과 같은 최종 상태가 우연히 만들어졌지만 18개 요청은 정상 응답을 받지 못했다.

## 대안 판단

### Scrap 행 잠금

초기 상태에는 잠글 scrap 행이 없으므로 동시 INSERT 경쟁을 막을 수 없다.

### Article 행 잠금

같은 기사를 스크랩하는 모든 사용자가 하나의 article 행에서 대기한다. 인기 기사일수록 사용자 간 불필요한 경합 범위가 커진다.

### User 행 잠금

사용자 행은 스크랩 여부와 관계없이 존재한다. 동일 사용자의 짧은 toggle transaction만 직렬화하고, 서로 다른 사용자는 같은 기사를 동시에 스크랩할 수 있다. DB row lock이므로 단일·다중 애플리케이션 인스턴스에서 같은 MySQL을 사용할 때 동일하게 적용된다.

현재 규모와 API 계약에서는 `PESSIMISTIC_WRITE` user 행 잠금이 가장 작은 변경이다. Redis 분산 락, Kafka, 별도 queue는 필요하지 않다.

## 변경

`UserRepository.findByIdForUpdate()`에 `PESSIMISTIC_WRITE`를 적용하고 `ScrapService.toggle()`이 transaction 시작 후 사용자 행을 먼저 잠그도록 변경했다.

```text
user SELECT FOR UPDATE
article 조회
scrap 조회
INSERT 또는 DELETE
commit 후 다음 동일 user 요청 진행
```

article 행을 잠그지 않으므로 서로 다른 사용자의 같은 기사 요청은 독립적으로 처리된다.

## 개선 후

개선 전과 같은 20건 조건을 실행한 결과다.

| 지표 | 개선 전 | 개선 후 |
| --- | ---: | ---: |
| 성공 | 2 | 20 |
| 실패 | 18 | 0 |
| `scrapped=true` | 1 | 10 |
| `scrapped=false` | 1 | 10 |
| 최종 scrap 행 | 0 | 0 |

20개 요청이 DB에서 순서대로 실행되어 기존 toggle 의미인 `추가 → 취소`를 10회 반복했다. 요청 수가 짝수이므로 최종 행은 0건이며 모든 호출이 정상 완료됐다.

추가 회귀 테스트로 다음을 확인했다.

- 순차 호출은 기존처럼 첫 호출 `true`, 두 번째 호출 `false`를 반환한다.
- 서로 다른 사용자 두 명은 같은 기사를 동시에 스크랩하고 각각 한 행을 유지한다.
- 존재하지 않는 사용자와 기사는 기존과 동일한 not-found 예외를 반환한다.
- 기존 스크랩 목록 projection과 nullable source 동작을 포함한 `ScrapQueryIntegrationTest` 7개가 통과한다.
- MySQL·Redis Testcontainers를 포함한 전체 80개 테스트가 통과한다.

## API 경계

POST toggle은 호출할 때마다 상태를 반전하므로 본질적으로 멱등하지 않다. 이번 변경은 동시 요청을 순차 실행해 기존 계약대로 처리하며, 중복 요청을 하나의 요청으로 취급하지 않는다.

같은 “스크랩 추가” 요청의 반복에도 최종 상태가 항상 추가되도록 만들려면 `PUT 추가 / DELETE 취소`처럼 의도를 분리한 API와 프론트 계약 변경이 필요하다. 이는 이번 동시성 정합성 수정에 포함하지 않는다.

## 해석 경계

- 이 결과는 MySQL 8 Testcontainers의 동일 사용자·동일 기사 20건 통제 조건이다.
- 운영 TPS나 사용자 클릭 빈도를 측정한 부하 테스트가 아니다.
- user 행 잠금은 동일 사용자의 toggle을 직렬화하므로 한 사용자가 비정상적으로 많은 스크랩 요청을 병렬 전송하면 대기 시간이 늘 수 있다.
- 일반 사용자의 짧은 transaction을 대상으로 하며 lock wait·timeout이 실제 운영 지표에서 나타날 때 별도 조정을 검토한다.
