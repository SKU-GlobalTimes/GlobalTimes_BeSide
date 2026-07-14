# Atomic View Count Consistency

## 목적

기사 상세 API에 동시에 들어온 성공 요청 수와 `article.view_count` 증가량이 일치하는지 확인하고, JPA read-modify-write에서 발생하는 lost update를 DB 원자 UPDATE로 개선한다.

## 환경과 조건

- 로컬 Spring Boot 단일 인스턴스: `localhost:8080`
- MySQL 8 Docker, transaction isolation: `REPEATABLE-READ`
- k6 `shared-iterations`: 20 VU, 총 20 요청
- 테스트 기사: 다른 도메인 데이터에서 참조되지 않는 기사 사용
- 각 측정 전 조회수를 백업하고 측정 후 원래 값으로 복원

```powershell
$env:BASE_URL='http://127.0.0.1:8080'
$env:ARTICLE_ID='<test-article-id>'
$env:VUS='20'
$env:REQUESTS='20'
k6 run --quiet load-tests/k6/view-count-consistency.js
```

## 개선 전

`DetailService.getNewsDetail()`은 기사를 조회한 뒤 엔티티의 `viewCount`를 증가시키고 저장했다. 동시 요청이 같은 이전 값을 읽으면 마지막 UPDATE들이 서로의 증가분을 덮어쓸 수 있다.

| 성공 요청 | 기대 증가량 | 실제 증가량 | 유실 |
| ---: | ---: | ---: | ---: |
| 20 | 20 | 2 | 18 |

HTTP 요청은 20건 모두 성공했지만 조회수는 2건만 증가했다.

## 개선

Repository가 다음 의미의 단일 UPDATE를 실행하게 변경했다.

```sql
UPDATE article
SET view_count = view_count + 1
WHERE article_id = ?;
```

UPDATE 영향 행이 0이면 기존 기사 없음 오류를 반환하고, 성공하면 기사를 다시 조회해 증가 완료된 조회수를 응답에 포함한다.

## 개선 후

| 성공 요청 | 기대 증가량 | 실제 증가량 | 유실 |
| ---: | ---: | ---: | ---: |
| 20 | 20 | 20 | 0 |

동일한 20 VU·20요청에서 모든 요청이 성공했고 조회수 증가량도 20과 일치했다. 테스트 기사 조회수는 측정 후 기준값으로 복원했다.

## 판단과 한계

- 조회수처럼 단일 숫자를 증가시키는 쓰기에는 비관적 락이나 낙관적 락 재시도보다 DB 원자 UPDATE가 단순하다.
- 이 결과는 로컬 단일 인스턴스의 정합성 검증이며 최대 처리량 측정이 아니다.
- 인기 기사 한 행에 훨씬 높은 쓰기 경합이 확인되기 전에는 Redis counter나 비동기 집계를 도입하지 않는다.
