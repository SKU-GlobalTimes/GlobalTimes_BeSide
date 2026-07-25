# Perspectives FULLTEXT EXPLAIN 분석

## 목적

이 문서는 Perspectives API에서 캐시가 적중하지 않았을 때 실행되는 MySQL 쿼리의 현재 실행 계획을 기록한다.
더 무거운 검색 기술이나 추가 캐시 계층을 도입하기 전에 의도한 FULLTEXT 인덱스가 사용되는지 확인하는 것이 목적이다.

## 대상 쿼리

`ArticleRepository.findPerspectives`는 다음 native query를 사용한다.

```sql
SELECT *
FROM article
WHERE article_id != :excludeId
  AND MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE)
ORDER BY published_at DESC
LIMIT 50;
```

이 쿼리는 다음 API에서 사용한다.

```text
GET /api/news/{id}/perspectives
```

## 관련 인덱스

`FullTextIndexConfig`는 인덱스가 없을 때 다음 인덱스를 생성한다.

```sql
CREATE FULLTEXT INDEX ft_article_title_description
ON article(title, description);
```

로컬 개발 DB 확인 결과:

| 항목 | 값 |
| --- | --- |
| 테이블 | `article` |
| 전체 행 | `9853` |
| FULLTEXT 인덱스 | `ft_article_title_description` |
| 인덱스 대상 컬럼 | `title`, `description` |

## EXPLAIN 결과

대표 쿼리:

```sql
EXPLAIN
SELECT *
FROM article
WHERE article_id != 8449
  AND MATCH(title, description) AGAINST('+war' IN BOOLEAN MODE)
ORDER BY published_at DESC
LIMIT 50;
```

결과 요약:

| 필드 | 값 |
| --- | --- |
| `type` | `fulltext` |
| `possible_keys` | `PRIMARY`, `ft_article_title_description` |
| `key` | `ft_article_title_description` |
| `rows` | `1` |
| `Extra` | `Using where; Ft_hints: no_ranking; Using filesort` |

해석:

- MySQL은 의도한 FULLTEXT 인덱스를 사용한다.
- 쿼리는 전체 테이블 스캔으로 대체되지 않는다.
- FULLTEXT 매칭 후 `published_at DESC`로 정렬하므로 `Using filesort`가 나타난다.
- 이 FULLTEXT 예시에서 옵티마이저의 예상 행 수는 신뢰하기 어렵다. 실제 매칭 수가 더 많아도 `rows=1`로 예상했다.

## EXPLAIN ANALYZE 결과

### 키워드: `+war`

매칭 수:

```text
404 rows
```

실행 계획 요약:

```text
Full-text index search on article using ft_article_title_description
actual time=0.243..9.48 rows=404

Filter
actual time=0.267..9.73 rows=404

Sort row IDs: article.published_at DESC
actual time=10.2..10.3 rows=50

Limit: 50
actual time=10.2..10.3 rows=50
```

### 키워드: `+economy`

매칭 수:

```text
39 rows
```

실행 계획 요약:

```text
Full-text index search on article using ft_article_title_description
actual time=0.0549..1.02 rows=39

Filter
actual time=0.0584..1.05 rows=39

Sort row IDs: article.published_at DESC
actual time=1.22..1.36 rows=39

Limit: 50
actual time=1.22..1.37 rows=39
```

## 결정

이 PR에서는 쿼리나 인덱스를 변경하지 않는다.

근거:

- 의도한 FULLTEXT 인덱스를 사용하고 있다.
- 현재 로컬 데이터셋에서 대표 쿼리는 낮은 밀리초 범위로 완료된다.
- #127에서 측정한 cold cache 지연은 이 쿼리만으로 설명되지 않는다.
- 순위, 최신성, 다국어 매칭에 영향을 주는 쿼리 변경은 별도의 품질 및 성능 기준으로 다뤄야 한다.

## 후속 후보

- 수동 키워드뿐 아니라 더 많은 기사 ID와 추출 키워드로 측정한다.
- FULLTEXT 검색 시간을 서비스 로그의 `translatedSearchMs`, `originalSearchMs`와 비교한다.
- 매칭 수가 증가해 정렬 시간이 p95에 드러날 때만 `Using filesort`를 조사한다.
- `searchByDescriptionOrTitleWithExploreFilters`는 국가·카테고리·날짜 선택 조건과 FULLTEXT를 결합하므로 별도로 분석한다.
- 언어 간 관련 기사 누락처럼 구체적인 FULLTEXT 품질 한계를 문서화한 뒤에만 Elasticsearch나 vector search를 검토한다.

## 안전한 명령 템플릿

운영 비밀 값이 아니라 로컬 환경의 개발용 인증 정보를 사용한다.
셸 기록에 비밀번호가 남을 수 있으므로 명령에 비밀번호를 직접 넣지 않는다.
비밀번호 없이 `-p`를 사용하고 대화형으로 입력한다.

```powershell
docker exec -it globaltimes_beside-mysql-1 mysql -uroot -p <database> -e "EXPLAIN SELECT * FROM article WHERE article_id != 8449 AND MATCH(title, description) AGAINST('+war' IN BOOLEAN MODE) ORDER BY published_at DESC LIMIT 50"
```
