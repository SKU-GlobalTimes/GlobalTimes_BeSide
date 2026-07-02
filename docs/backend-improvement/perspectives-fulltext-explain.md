# Perspectives FULLTEXT EXPLAIN analysis

## Purpose

This document records the current MySQL execution plan for the Perspectives API cache-miss query.
The goal is to check whether the query uses the intended FULLTEXT index before introducing heavier search technologies or additional caching layers.

## Target query

`ArticleRepository.findPerspectives` uses this native query:

```sql
SELECT *
FROM article
WHERE article_id != :excludeId
  AND MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE)
ORDER BY published_at DESC
LIMIT 50;
```

The query is used by:

```text
GET /api/news/{id}/perspectives
```

## Related index

`FullTextIndexConfig` creates this index if it does not already exist:

```sql
CREATE FULLTEXT INDEX ft_article_title_description
ON article(title, description);
```

Local dev DB check:

| Item | Value |
| --- | --- |
| Table | `article` |
| Total rows | `9853` |
| FULLTEXT index | `ft_article_title_description` |
| Indexed columns | `title`, `description` |

## EXPLAIN result

Representative query:

```sql
EXPLAIN
SELECT *
FROM article
WHERE article_id != 8449
  AND MATCH(title, description) AGAINST('+war' IN BOOLEAN MODE)
ORDER BY published_at DESC
LIMIT 50;
```

Result summary:

| Field | Value |
| --- | --- |
| `type` | `fulltext` |
| `possible_keys` | `PRIMARY`, `ft_article_title_description` |
| `key` | `ft_article_title_description` |
| `rows` | `1` |
| `Extra` | `Using where; Ft_hints: no_ranking; Using filesort` |

Interpretation:

- MySQL uses the intended FULLTEXT index.
- The query does not fall back to a full table scan.
- `Using filesort` appears because results are ordered by `published_at DESC` after FULLTEXT matching.
- The optimizer row estimate is not reliable for these FULLTEXT examples: `rows=1` was estimated even when actual matches were larger.

## EXPLAIN ANALYZE result

### Keyword: `+war`

Match count:

```text
404 rows
```

Execution plan summary:

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

### Keyword: `+economy`

Match count:

```text
39 rows
```

Execution plan summary:

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

## Decision

Do not change the query or index in this PR.

Reasons:

- The intended FULLTEXT index is being used.
- For the current local dataset, representative queries complete in low milliseconds.
- The current cold cache latency measured in #127 is not explained by this query alone.
- Query changes that affect ranking, recency, or multilingual matching should be handled with separate quality and performance criteria.

## Follow-up candidates

- Measure more article IDs and extracted keywords, not only manual keywords.
- Compare FULLTEXT search time with the service log fields `translatedSearchMs` and `originalSearchMs`.
- Investigate `Using filesort` only if match counts grow enough to make sort time visible in p95.
- Analyze `searchByDescriptionOrTitleWithExploreFilters` separately because it combines FULLTEXT with optional country/category/date filters.
- Consider Elasticsearch or vector search only after documenting concrete FULLTEXT quality gaps, such as missing related articles across languages.

## Safe command template

Use local development credentials from the local environment, not production secrets.
Avoid placing the password directly in the command because shell history can keep it.
Use `-p` without the password and enter the password interactively.

```powershell
docker exec -it globaltimes_beside-mysql-1 mysql -uroot -p <database> -e "EXPLAIN SELECT * FROM article WHERE article_id != 8449 AND MATCH(title, description) AGAINST('+war' IN BOOLEAN MODE) ORDER BY published_at DESC LIMIT 50"
```
