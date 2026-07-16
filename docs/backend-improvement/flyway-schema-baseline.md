# Flyway Schema Baseline

## 문제

기존 스키마는 Hibernate DDL과 애플리케이션 시작 시 실행되는 `FullTextIndexConfig`에 의존했다.
따라서 빈 DB, 개발 DB, CI DB가 같은 테이블과 인덱스를 갖는지 migration 버전으로 재현하고 검증하기 어려웠다.

## 적용 범위

- Flyway 10.10.0과 MySQL 지원 모듈 추가
- `V1__baseline_schema.sql`에서 5개 도메인 테이블과 FK, 일반 인덱스, 기사 FULLTEXT 인덱스 생성
- `V2__normalize_schema_objects.sql`에서 기존 DB의 Hibernate식 객체 이름을 canonical 이름으로 정규화하고 누락된 FULLTEXT 인덱스 생성
- Hibernate `ddl-auto=validate`로 Entity와 migration schema 일치 여부 검증
- 런타임 FULLTEXT DDL을 수행하던 `FullTextIndexConfig` 제거
- Testcontainers의 빈 MySQL에서 V1부터 schema 재현

URL UNIQUE, 기존 중복 데이터 정리, 신규 기능 schema, 운영 배포 설정은 이번 기준선 범위에서 제외했다.

## 빈 DB 검증

`ArticleRepositoryIntegrationTest`는 테스트 전용 MySQL 8에 Flyway V1을 적용한다.

- Flyway version 1, 2 성공 이력: 2건
- 도메인 테이블: 5개
- FULLTEXT 컬럼명과 순서: `title`, `description`
- 동시 조회수 원자 증가와 transaction rollback 회귀 테스트 포함
- 전체 Gradle 테스트: 49개 통과, 약 1분 10초

## 기존 로컬 DB Baseline

비어 있지 않은 기존 DB에는 V1 DDL을 다시 실행하지 않고 아래 옵션을 한 번만 사용해 version 1 baseline을 등록했다.

```powershell
./gradlew bootRun --args="--spring.flyway.baseline-on-migrate=true --spring.flyway.baseline-version=1 --server.port=0"
```

`baseline-on-migrate`는 잘못된 DB를 자동 baseline하는 안전장치 손실을 피하기 위해 기본 설정에 남기지 않는다.

적용 결과:

- `flyway_schema_history`: version `1`, type `BASELINE`, success `1`
- article: 9853 -> 9853
- source: 673 -> 673
- users: 1 -> 1
- scrap: 1 -> 1
- chat_history: 4 -> 4

baseline 등록 후 V2를 적용해 FK·보조 인덱스·unique 인덱스 이름을 canonical 형태로 통일하고 FULLTEXT 구조를 검증했다.

- Flyway version `2`, type `SQL`, success `1`
- FK: `fk_article_source`, `fk_scrap_user`, `fk_scrap_article`, `fk_chat_user`, `fk_chat_article`
- 주요 인덱스: `uk_source_name`, `idx_article_source_id`, `idx_scrap_article_id`, `idx_chat_article_id`
- FULLTEXT: `ft_article_title_description(title, description)`

V2 전후 도메인 행 수는 동일하다. V2는 의미가 같은 기존 객체의 이름을 정규화하고 누락된 FULLTEXT를 생성하며, canonical 이름에 예상과 다른 구조가 이미 있으면 migration을 실패시킨다.

FK는 전체 컬럼·참조 컬럼의 개수와 순서, 참조 테이블, match/update/delete rule을 함께 비교한다. V2 시작 시 이전 실패에서 남은 procedure를 제거하므로 구조 수정과 Flyway repair 후 재실행할 수 있다.
인덱스 검증 범위는 컬럼명·순서, unique 여부와 index type이다. 현재 사용하지 않는 prefix length, ASC/DESC, visibility는 비교하지 않는다.

## 이후 변경 원칙

schema 변경은 적용 순서를 보존하는 새 migration으로 추가한다. 이미 적용된 V1 파일은 수정하지 않는다.
DB 제약을 새로 도입할 때는 기존 데이터 위반 여부와 정리 방식을 먼저 별도 Issue에서 검증한다.
