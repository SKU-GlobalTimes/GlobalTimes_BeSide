# MySQL Testcontainers Integration Test

## 목적

mock 단위 테스트나 수동 API 측정만으로는 확인하기 어려운 MySQL 트랜잭션 동작을 테스트 전용 실제 MySQL에서 자동 검증한다.

첫 적용 대상은 #198에서 도입한 기사 조회수 원자 증가다.

## 실행 조건

- Docker Engine이 실행 중이어야 한다.
- 개발용 MySQL/Redis compose container는 실행하지 않아도 된다.
- 첫 실행은 Testcontainers 의존성과 image 준비로 이후 실행보다 오래 걸릴 수 있다.

```powershell
./gradlew test --tests "com.example.globalTimes_be.domain.article.repository.ArticleRepositoryIntegrationTest"
```

## 테스트 구조

- `@DataJpaTest`: Repository와 JPA 관련 bean만 로드
- `MySQLContainer("mysql:8.0")`: 테스트 전용 임시 MySQL 사용
- `@ServiceConnection`: 동적 JDBC URL과 인증 정보를 Spring Boot에 자동 연결
- `ddl-auto=create`: 임시 database에 schema 생성
- `TransactionTemplate`: worker별 독립 transaction과 rollback 경계 구성

개발용 `application.yml`의 datasource URL이나 로컬 MySQL 계정을 테스트에 복사하지 않는다.
컨테이너가 제거될 때 database도 함께 폐기되므로 종료 시 `create-drop` DDL은 실행하지 않는다.

## 검증 결과

### 동시 원자 증가

- 같은 기사에 20개 worker가 각각 `incrementViewCount()` 실행
- 각 UPDATE 영향 행: 1
- 기대 조회수: 20
- 실제 조회수: 20

### 실패 rollback

- transaction 안에서 조회수 원자 증가 후 강제 예외 발생
- transaction 종료 후 기대 조회수: 0
- 실제 조회수: 0

## 격리와 정리

- fixture는 임시 MySQL에만 저장한다.
- 각 테스트 후 article/source fixture를 정리한다.
- 테스트 종료 후 MySQLContainer와 Ryuk가 자동 제거되는 것을 확인했다.
- 기존 `globaltimes_beside-mysql-1`, `globaltimes_beside-redis-1`에는 변경이 없다.

## 범위와 후속 기준

- 전체 `@SpringBootTest`, API E2E, Redis/Kafka container는 이번 범위에서 제외한다.
- MySQL 고유 query, DB constraint, transaction rollback처럼 mock이나 H2로 의미가 약한 경로부터 통합 테스트를 추가한다.
- 모든 서비스 단위 테스트를 container 테스트로 바꾸지 않는다.
