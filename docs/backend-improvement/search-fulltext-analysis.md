# Search API FULLTEXT analysis

## Purpose

This document records the current MySQL FULLTEXT behavior of `GET /api/search`.
The goal is to explain how the search query behaves by keyword and to document a small optimization for cases where the original search text and translated text are the same.

## Target flow

```text
SearchController
-> TranslationService.translateToEnglish(text)
-> SearchArticlesService.getSearchArticles(...)
-> ArticleRepository.searchByDescriptionOrTitleWithExploreFilters(...)
```

The API searches both the original text and the English translation.

## Previous query shape

`searchByDescriptionOrTitleWithExploreFilters` used two FULLTEXT predicates joined by `OR`:

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

This is necessary when the original text and translated text differ.
However, for English inputs such as `war`, translation can return the same text.
In that case, the previous query effectively repeated the same FULLTEXT predicate:

```sql
MATCH(...) AGAINST('war' ...)
OR MATCH(...) AGAINST('war' ...)
```

## Finding

On the local development dataset, a single `MATCH` query used the FULLTEXT index.
The duplicated `OR MATCH` query chose a reverse scan on `idx_article_published_at` and applied MATCH as a filter.
The representative `EXPLAIN ANALYZE` examples below use the default search path without optional `country`, `category`, or `date` filters.
Queries with those filters can choose different execution plans depending on selectivity.

Dataset:

| Item | Value |
| --- | --- |
| Table | `article` |
| Total rows | `9853` |
| FULLTEXT index | `ft_article_title_description(title, description)` |

Keyword match counts:

| Keyword | Match count |
| --- | --- |
| `war` | 404 |
| `technology` | 82 |
| `economy` | 39 |
| `대구` OR `daegu` | 0 |

### Single MATCH

```sql
EXPLAIN ANALYZE
SELECT *
FROM article
WHERE MATCH(title, description) AGAINST('war' IN BOOLEAN MODE)
ORDER BY published_at DESC
LIMIT 100;
```

Summary:

```text
Full-text index search on article using ft_article_title_description
actual time=0.575..7.04 rows=404

Sort row IDs: article.published_at DESC
actual time=10.3..10.7 rows=100
```

### Duplicated OR MATCH

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

Summary:

```text
Index scan on article using idx_article_published_at (reverse)
actual time=0.233..25.2 rows=4186

Filter
actual time=2.75..27.1 rows=100
```

Interpretation:

- Single MATCH uses the intended FULLTEXT index.
- Duplicated OR MATCH can make MySQL prefer the `published_at` index and evaluate MATCH as a filter.
- This is visible for English inputs where original and translated text are effectively the same.

## Change

`SearchArticlesService` now checks whether `text` and `translatedText` are the same after trimming and case-insensitive comparison.

- If they are the same, it calls a single-MATCH repository query.
- If they differ, it keeps the existing OR-MATCH query to preserve multilingual search behavior.

No API response structure changed.

## API check

Representative API calls after the previous #123 fix:

| Request | Status | Notes |
| --- | --- | --- |
| `/api/search?text=war` | 200 | English, many matches |
| `/api/search?text=economy` | 200 | English, fewer matches |
| `/api/search?text=technology` | 200 | English, medium matches |
| `/api/search?text=대구` | 200 | Translates to `daegu`, zero current FULLTEXT matches |

## Decision

Keep the OR-MATCH query for different original/translated terms, but avoid duplicated OR-MATCH when both terms are the same.

Reasons:

- It is a small scoped change.
- It preserves the response shape and existing multilingual search behavior.
- It lets English same-term searches use the intended FULLTEXT index.
- Broader search quality topics, such as Korean morphology, multilingual ranking, Elasticsearch, or vector search, remain separate follow-up work.

## Follow-up candidates

- Measure `/api/search` p95 before/after for same-term English searches with k6.
- Analyze OR-MATCH cases where original and translated terms differ.
- Compare current FULLTEXT results with expected results for representative Korean and multilingual queries.
- Consider a query rewrite with `UNION` only if OR-MATCH remains a measurable bottleneck.
- Define search quality samples before introducing Elasticsearch or semantic search.
