# 검색 API FULLTEXT 분석

## 목적

이 문서는 `GET /api/search`의 현재 MySQL FULLTEXT 동작을 기록한다. 검색어에 따라 쿼리가 어떻게 실행되는지 설명하고, 원문 검색어와 번역 검색어가 같은 경우의 작은 최적화를 문서화하는 것이 목적이다.

## 대상 흐름

```text
SearchController
-> TranslationService.translateToEnglish(text)
-> SearchArticlesService.getSearchArticles(...)
-> ArticleRepository.searchByDescriptionOrTitleWithExploreFilters(...)
```

API는 원문과 영어 번역문을 모두 검색한다.

## 기존 쿼리 형태

`searchByDescriptionOrTitleWithExploreFilters`는 두 FULLTEXT 조건을 `OR`로 연결했다.

```sql
SELECT *
FROM article
WHERE (
  MATCH(title, description) AGAINST(:text1 IN BOOLEAN MODE)
  OR MATCH(title, description) AGAINST(:text2 IN BOOLEAN MODE)
)
AND (:country IS NULL OR country = :country)
AND (:category IS NULL OR category = :category)
AND (:dateFrom IS NULL OR published_at >= :dateFrom)
AND (:dateTo IS NULL OR published_at <= :dateTo)
ORDER BY published_at DESC
LIMIT 100;
```

원문과 번역문이 다를 때는 이 형태가 필요하다. 하지만 `war` 같은 영어 입력은 번역 결과가 원문과 같을 수 있다. 이 경우 기존 쿼리는 사실상 같은 FULLTEXT 조건을 반복한다.

```sql
MATCH(...) AGAINST('war' ...)
OR MATCH(...) AGAINST('war' ...)
```

## 확인 결과

로컬 개발 데이터에서 단일 `MATCH` 쿼리는 FULLTEXT index를 사용했다. 중복 `OR MATCH` 쿼리는 `idx_article_published_at` 역방향 scan을 선택하고 `MATCH`를 filter로 적용했다.

아래 대표 `EXPLAIN ANALYZE`는 선택적인 `country`, `category`, `date` filter가 없는 기본 검색 경로다. 해당 filter를 사용하면 선택도에 따라 다른 실행 계획이 선택될 수 있다.

데이터:

| 항목 | 값 |
| --- | --- |
| Table | `article` |
| 전체 row | `9853` |
| FULLTEXT index | `ft_article_title_description(title, description)` |

검색어별 일치 수:

| 검색어 | 일치 수 |
| --- | --- |
| `war` | 404 |
| `technology` | 82 |
| `economy` | 39 |
| `대구` OR `daegu` | 0 |

### 단일 MATCH

```sql
EXPLAIN ANALYZE
SELECT *
FROM article
WHERE MATCH(title, description) AGAINST('war' IN BOOLEAN MODE)
ORDER BY published_at DESC
LIMIT 100;
```

요약:

```text
Full-text index search on article using ft_article_title_description
actual time=0.575..7.04 rows=404

Sort row IDs: article.published_at DESC
actual time=10.3..10.7 rows=100
```

### 중복 OR MATCH

```sql
EXPLAIN ANALYZE
SELECT *
FROM article
WHERE (
  MATCH(title, description) AGAINST('war' IN BOOLEAN MODE)
  OR MATCH(title, description) AGAINST('war' IN BOOLEAN MODE)
)
ORDER BY published_at DESC
LIMIT 100;
```

요약:

```text
Index scan on article using idx_article_published_at (reverse)
actual time=0.233..25.2 rows=4186

Filter
actual time=2.75..27.1 rows=100
```

해석:

- 단일 `MATCH`는 의도한 FULLTEXT index를 사용한다.
- 중복 `OR MATCH`는 MySQL이 `published_at` index를 선택하고 `MATCH`를 filter로 평가하게 만들 수 있다.
- 이 차이는 원문과 번역문이 사실상 같은 영어 입력에서 나타난다.

## 변경

`SearchArticlesService`는 `text`와 `translatedText`를 trim하고 대소문자를 무시해 비교한다.

- 두 값이 같으면 단일 `MATCH` repository query를 호출한다.
- 두 값이 다르면 기존 다국어 검색 동작을 유지하기 위해 `OR MATCH` query를 사용한다.

API 응답 구조는 변경하지 않았다.

## API 확인

#123 수정 이후의 대표 호출:

| 요청 | 상태 | 설명 |
| --- | --- | --- |
| `/api/search?text=war` | 200 | 영어, 다수 결과 |
| `/api/search?text=economy` | 200 | 영어, 비교적 적은 결과 |
| `/api/search?text=technology` | 200 | 영어, 중간 규모 결과 |
| `/api/search?text=대구` | 200 | `daegu`로 번역되지만 현재 FULLTEXT 결과는 없음 |

## 판단

원문과 번역문이 다르면 `OR MATCH`를 유지하되, 두 값이 같을 때는 중복 `OR MATCH`를 피한다.

근거:

- 범위가 작은 변경이다.
- 응답 구조와 기존 다국어 검색 동작을 유지한다.
- 영어 동일 검색어가 의도한 FULLTEXT index를 사용할 수 있다.
- 한국어 형태소, 다국어 ranking, Elasticsearch, vector search 같은 검색 품질 주제는 별도 후속 작업으로 남긴다.

## 후속 후보

- k6로 동일 영어 검색어의 `/api/search` p95 전후 비교
- 원문과 번역문이 다른 `OR MATCH` 사례 분석
- 대표 한국어·다국어 질의의 현재 FULLTEXT 결과와 기대 결과 비교
- `OR MATCH`가 반복 측정에서 병목으로 확인될 때만 `UNION` query rewrite 검토
- Elasticsearch 또는 semantic search 도입 전에 검색 품질 표본 정의
