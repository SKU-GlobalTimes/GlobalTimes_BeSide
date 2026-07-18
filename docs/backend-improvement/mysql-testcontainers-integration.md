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
- Flyway V1/V2 migration: 임시 database에 실제 MySQL schema를 만들고 constraint/index 이름과 FULLTEXT 인덱스를 canonical 구조로 수렴
- `ddl-auto=validate`: Hibernate가 Entity와 migration schema의 일치 여부 검증
- `TransactionTemplate`: worker별 독립 transaction과 rollback 경계 구성

개발용 `application.yml`의 datasource URL이나 로컬 MySQL 계정을 테스트에 복사하지 않는다.
컨테이너가 제거될 때 database도 함께 폐기되므로 Hibernate의 create/create-drop DDL은 사용하지 않는다.

## 검증 결과

### Flyway schema 재현

- 빈 MySQL 8에 V1 migration 적용
- `flyway_schema_history`의 version 1, 2 성공 이력 확인
- 5개 domain table 생성 확인
- `ft_article_title_description` FULLTEXT 인덱스가 `title`, `description` 순서인지 확인

### 기존 DB baseline 재현

- Hibernate 생성 이름을 가진 5개 domain table과 FULLTEXT가 없는 legacy fixture 구성
- version 1 baseline 등록 후 V2만 실행
- 5개 FK와 주요 보조·unique 인덱스가 canonical 이름으로 정규화되는지 확인
- 누락된 `ft_article_title_description(title, description)` 생성 확인
- 잘못된 canonical index와 FK rule이 migration을 실패시키는지 확인
- 실패 후 구조 수정, Flyway repair, V2 재실행 및 procedure 정리를 확인
- 집중 테스트 6개와 전체 49개 테스트 통과

### 기사 URL 유일성 V3

- V2 상태의 임시 DB에 동일 URL 기사 2건을 넣고 V3가 최신 `article_id` 1건만 남기는지 확인
- 대소문자만 다른 URL은 raw SHA-256 hash가 다르므로 별도 행으로 유지되는지 확인
- 삭제 후보에 summary 또는 scrap 참조가 있으면 V3가 실패하고 원본·참조 데이터를 유지하는지 확인
- 잘못된 generated column에서는 삭제 전에 실패하고 구조 수정·Flyway repair·재실행이 가능한지 확인
- V3 적용 후 동일 URL 동시 INSERT 2건 중 1건만 성공하고 최종 1행만 남는지 확인
- `uk_article_url_hash(url_hash)` UNIQUE와 신규 DB V1→V2→V3 이력 확인
- 집중 테스트 10개와 전체 53개 테스트 통과

### News API 수집 부분 실패 E2E

- random-port JDK mock HTTP server에서 headline category별 200, 500, timeout 응답을 통제
- 실제 RestTemplate, JSON 역직렬화, Source/Article JPA 저장, Flyway V3 URL UNIQUE를 MySQL 8 Testcontainer에서 검증
- 일부 요청 실패 후에도 정상 기사 2건 저장, 같은 수집 재실행 후 기사 2건과 URL 중복 그룹 0건 유지
- 기존 compose DB와 8080 서버, 실제 News API key를 사용하지 않으며 전체 54개 테스트 통과

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
- Testcontainers 검증은 기존 `globaltimes_beside-mysql-1`, `globaltimes_beside-redis-1`의 데이터에 접근하지 않는다.
- #208에서 기존 local MySQL에는 별도 1회 baseline 절차로 `flyway_schema_history`만 추가했으며 도메인 데이터 행 수는 유지됐다.

## 범위와 후속 기준

- 전체 `@SpringBootTest`, API E2E, Redis/Kafka container는 이번 범위에서 제외한다.
- MySQL 고유 query, DB constraint, transaction rollback처럼 mock이나 H2로 의미가 약한 경로부터 통합 테스트를 추가한다.
- 모든 서비스 단위 테스트를 container 테스트로 바꾸지 않는다.
