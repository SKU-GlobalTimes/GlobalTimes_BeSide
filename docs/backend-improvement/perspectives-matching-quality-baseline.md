# Perspectives 매칭 품질 기준선

## 목적

`GET /api/news/{id}/perspectives`는 유사한 이슈를 다루는 여러 국가의 기사를 보여주기 위한 API다.
현재 구현은 제목 키워드, 선택적 영어 번역, MySQL FULLTEXT 검색을 사용한다.

이 문서는 Elasticsearch, vector search, RAG, issue clustering처럼 더 무거운 검색 계층을 도입하기 전에 현재 매칭 품질을 측정할 작은 기준선을 정의한다.

첫 로컬 MySQL snapshot은 `perspectives-matching-sample-snapshot.md`에 기록한다.
Snapshot을 해석할 때는 `perspectives-source-coverage-limit-log.md`도 적용해 검색 품질과 출처 범위·최신성 한계를 분리한다.

## 현재 흐름

```text
PerspectivesService.getPerspectives(articleId)
-> ArticleRepository.findById(articleId)
-> KeywordExtractor.extractPlain(base.title)
-> KeywordExtractor.extract(base.title)
-> if base.language != en, TranslationService.translateToEnglish(plainKeyword)
-> ArticleRepository.findPerspectives(articleId, translatedOrOriginalBooleanKeyword)
-> if translated keyword differs from original keyword, run original keyword search too
-> merge duplicate articles
-> group by country
-> limit each country to 3 articles
-> cache response in Redis
```

## 현재 검색 형태

`ArticleRepository.findPerspectives`는 다음 FULLTEXT 쿼리를 사용한다.

```sql
SELECT *
FROM article
WHERE article_id != :excludeId
  AND MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE)
ORDER BY published_at DESC
LIMIT 50;
```

확인된 동작:

- 로컬 개발 데이터셋에서 `ft_article_title_description(title, description)` FULLTEXT 인덱스를 사용한다.
- 결과는 의미 유사도가 아니라 `published_at DESC`로 정렬한다.
- `KeywordExtractor`는 제목 token을 최대 4개 유지하고 BOOLEAN MODE에서 앞의 2개를 필수 검색어로 만든다.
- 영어가 아닌 기준 기사는 번역한 영어 키워드로 검색하고, 원문과 다르면 원문 키워드로도 검색한다.
- 검색 기사를 국가별로 묶고 국가당 최대 3개를 유지한다.

## 확인된 한계

- 제목의 표현, entity 또는 언어가 다르면 유사한 이슈를 놓칠 수 있다.
- FULLTEXT 매칭은 기사가 동일 사건을 설명한다고 보장하지 않는다.
- 최종 정렬이 최신순이므로 결과가 최신이지만 관련성이 약할 수 있다.
- 현재 모델에는 안정적인 `issue_id`나 기사 cluster 식별자가 없다.
- 기준 기사에서 추출한 키워드만 영어로 번역하므로 영어 매칭에 유리하고 모든 기사 언어를 완전히 다루지 못한다.
- 매칭 품질은 수집 출처 범위에도 제한된다. 누락되거나 늦게 들어온 RSS/News API 기사는 FULLTEXT나 semantic search만으로 복구할 수 없다.

## 표본 선정 기준

성공 사례만 의도적으로 고르지 않고 대표 기사 표본을 사용한다.

권장 표본:

| 표본 유형 | 최소 개수 | 이유 |
| --- | ---: | --- |
| 영어 국제 이슈 | 2 | 일반적인 대량 FULLTEXT 동작을 확인한다. |
| 한국어 또는 다른 비영어 기준 기사 | 2 | 번역과 원문 키워드 fallback을 확인한다. |
| 매칭이 적거나 없는 이슈 | 2 | recall 한계를 드러낸다. |
| 여러 국가의 속보/뉴스 주제 | 2 | 서로 다른 국가 출처가 나타나는지 확인한다. |
| 넓은 카테고리 용어 이슈 | 1 | 잡음 매칭과 최신순 편향을 드러낸다. |

표본마다 기준 기사 ID, 제목, 국가, 언어, 카테고리, 추출 키워드와 관찰 결과를 기록한다.
개인정보, API key 또는 외부 API 전체 응답은 포함하지 않는다.

## 측정 절차

1. Cold path를 측정할 때 표본 기사의 Redis key를 삭제한다.

```text
perspectives:article:{articleId}
```

2. API를 호출한다.

```http
GET /api/news/{articleId}/perspectives
```

3. 응답 요약과 서비스 log field를 수집한다.

```text
keywordLength
translationRequested
translationFallback
translationMs
translatedSearchMs
originalSearchMs
countriesFound
totalArticles
totalMs
```

4. 반환 국가 그룹을 수동 검토하고 각 표본을 분류한다.

| 분류 | 의미 |
| --- | --- |
| `good_match` | 반환 기사 대부분이 동일 이슈를 다루는 것으로 보인다. |
| `partial_match` | 유용한 관련 기사가 있지만 중요한 국가나 기사가 누락된다. |
| `weak_match` | 동일 이슈보다 넓은 주제 매칭이 대부분이다. |
| `no_match` | API가 유용한 관련 기사를 반환하지 않는다. |

## 기준선 결과 템플릿

| Article ID | Base country | Language | Category | Keyword | countriesFound | totalArticles | Classification | Notes |
| --- | --- | --- | --- | --- | ---: | ---: | --- | --- |
| TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |

국가별 결과 템플릿:

| Article ID | Country | Returned count | Match notes |
| --- | --- | ---: | --- |
| TBD | TBD | TBD | TBD |

## 결정 경계

이 기준선에서는 Elasticsearch, vector search, RAG, Kafka 또는 새 schema를 도입하지 않는다.
작은 FULLTEXT 또는 키워드 개선으로 해결할 수 없는 구체적인 품질 한계가 기준선에서 확인된 뒤에만 검토한다.

후속 결정 후보:

| 선택지 | 검토 조건 |
| --- | --- |
| FULLTEXT query tuning | 좋은 키워드가 있지만 query 형태나 정렬이 유용한 결과를 놓친다. |
| 더 나은 keyword extraction | 현재 제목 token의 잡음이 크거나 entity를 놓친다. |
| Multilingual query expansion | 비영어 기사 recall이 일관되게 낮다. |
| Vector search 또는 embeddings | 같은 이슈의 표현이 달라 FULLTEXT가 놓친다. |
| Issue clustering / `issue_id` | 수집 전반에서 안정적인 사건 단위 그룹이 필요하다. |
| Elasticsearch | 키워드 검색, 필터, 순위, 다국어 분석에 전용 검색 계층이 필요하다. |
