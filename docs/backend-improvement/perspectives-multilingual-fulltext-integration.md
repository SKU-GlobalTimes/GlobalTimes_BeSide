# Perspectives 다국어 FULLTEXT 통합 검증

## 목적

이 검증은 영어가 아닌 기준 기사에 대한 Perspectives 검색 경로의 백엔드 동작을 통제된 조건에서 확정한다.

```text
non-English base article
-> keyword extraction
-> mocked English translation
-> real MySQL FULLTEXT search
-> optional original-keyword search
-> deduplication and country grouping
```

이 검증은 의미 유사도를 측정하거나 매칭 품질을 개선하지 않는다. 결정적인 백엔드 경로 실패와 관련 기사 부재 또는 잘못된 번역 키워드 조건을 구분하는 것이 목적이다.

## 테스트 경계

| 구성 요소 | 테스트 동작 |
| --- | --- |
| `PerspectivesService` | 실제 서비스 구현 |
| `KeywordExtractor` | 실제 키워드 추출 |
| `ArticleRepository.findPerspectives` | 실제 native query |
| Database | Flyway FULLTEXT schema를 적용한 MySQL 8 Testcontainers |
| Translation | Mockito로 통제한 결과 또는 예외 |
| Redis | Cache TTL을 zero로 설정해 읽기·쓰기 없음 |
| Article data | 테스트 전용 Source 및 Article fixture |

이 테스트는 Google Translation, News API, RSS, compose database 또는 배포 서비스를 호출하지 않는다.

## 통제 시나리오

| 시나리오 | MySQL 관련 행 | 번역 조건 | 기대 결과 |
| --- | --- | --- | --- |
| 번역 성공 | 존재 | `Lunaris Summit Joint Statement` | 영어 관련 기사 반환 |
| 수집 범위 부재 | 없음 | 동일한 올바른 번역 | 기사 없음 |
| 잘못된 번역 | 존재 | `Football Transfer Market Opens` | 기사 없음 |
| 번역 실패 | 프랑스어 관련 행 존재 | 번역 예외 | 원문 프랑스어 FULLTEXT 결과 반환 |
| 번역/원문 중복 | 한 행이 두 키워드 집합에 모두 매칭 | 유효한 번역 키워드 | 최종 기사는 한 건이며 두 건이 아님 |

수집 범위 부재와 잘못된 번역 시나리오는 서로 다른 통제 입력에서 의도적으로 동일한 결과 없음 응답을 만든다. 실제 결과 없음 응답만으로는 추가 근거 없이 원인을 FULLTEXT, 번역, 수집 범위 중 하나로 단정할 수 없음을 보여준다.

## Fixture 해석

fixture 텍스트는 각 FULLTEXT 경계를 결정적으로 재현하도록 작성했다. 실제 편집 기사 표본이 아니며, 동일 쿼리가 수집 데이터셋에서 의미상 관련된 기사를 찾는다는 근거가 아니다.

프랑스어 fallback fixture는 공백으로 분리된 라틴 문자를 사용한다. 같은 검증에 한국어 tokenizer 동작까지 포함하지 않고 fallback orchestration에 집중하기 위해서다.

서비스 쿼리를 실행하기 전에 fixture를 commit한다. 서비스 호출은 transaction 안에서 실행하므로 native query와 lazy `Source` 접근이 실제 persistence 경로를 사용한다.

## 검증 가능한 주장과 불가능한 주장

이 검증으로 확인할 수 있는 내용:

- 영어가 아닌 입력에서 번역을 요청한다.
- 주어진 번역 키워드가 MySQL BOOLEAN MODE FULLTEXT까지 전달된다.
- 번역 실패 시 원문 키워드 fallback을 유지한다.
- 번역 결과와 원문 쿼리 결과를 기사 ID 중복 없이 병합한다.
- 국가별 그룹과 전체 개수가 병합 결과를 반영한다.

이 검증만으로 확인할 수 없는 내용:

- Google Translation이 정확한 키워드를 생성한다.
- 설정한 RSS 또는 News API 출처에 관련 사건 기사가 존재한다.
- 반환 기사가 실제 세계의 같은 사건을 다룬다.
- 다국어 recall, precision 또는 semantic similarity가 개선됐다.
- Elasticsearch, embeddings, Vector DB 또는 번역 결과 영속화가 필요하다.

실제 매칭 품질을 평가하려면 기대 관련 기사 ID를 포함한 별도 라벨 표본이 필요하다. 누락 결과를 해석하기 전에 출처 최신성과 로컬 수집 범위도 함께 기록해야 한다.
