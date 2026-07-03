# Perspectives matching quality baseline

## Purpose

`GET /api/news/{id}/perspectives` is intended to show articles from multiple countries about a similar issue.
The current implementation uses title keywords, optional English translation, and MySQL FULLTEXT search.

This document defines a small baseline for measuring the current matching quality before introducing heavier search layers such as Elasticsearch, vector search, RAG, or issue clustering.

The first local MySQL snapshot is recorded in `perspectives-matching-sample-snapshot.md`.

## Current Flow

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

## Current Search Shape

`ArticleRepository.findPerspectives` uses the following FULLTEXT query:

```sql
SELECT *
FROM article
WHERE article_id != :excludeId
  AND MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE)
ORDER BY published_at DESC
LIMIT 50;
```

Known behavior:

- The query uses the `ft_article_title_description(title, description)` FULLTEXT index on the local development dataset.
- Results are ordered by `published_at DESC`, not by semantic similarity.
- `KeywordExtractor` keeps at most 4 title tokens and makes the first 2 required terms in BOOLEAN MODE.
- Non-English base articles are searched once with translated English keywords and, when different, once with original keywords.
- The response groups the retrieved articles by country and keeps up to 3 articles per country.

## Known Limits

- Similar issues can be missed when titles use different expressions, entities, or languages.
- FULLTEXT matching does not guarantee that articles describe the same event.
- Search results can be recent but weakly related because the final ordering is recency-based.
- The current model has no stable `issue_id` or article cluster identity.
- Translating only the extracted base keywords to English favors English matching and does not fully cover all article languages.

## Sample Selection Criteria

Use representative article samples instead of cherry-picking only successful matches.

Recommended sample set:

| Sample type | Minimum count | Why |
| --- | ---: | --- |
| English global issue | 2 | Checks common high-volume FULLTEXT behavior. |
| Korean or other non-English base article | 2 | Checks translation plus original keyword fallback. |
| Low-match or zero-match issue | 2 | Exposes recall gaps. |
| Multi-country breaking/news topic | 2 | Checks whether different country sources appear. |
| Broad category term issue | 1 | Exposes noisy matches and recency bias. |

For each sample, record the base article ID, title, country, language, category, extracted keywords, and observed result summary.
Do not include private data, API keys, or full external API responses.

## Measurement Procedure

1. Clear the Redis key for the sample article when measuring the cold path:

```text
perspectives:article:{articleId}
```

2. Call the API:

```http
GET /api/news/{articleId}/perspectives
```

3. Capture the response summary and service log fields:

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

4. Review the returned country groups manually and classify each sample:

| Classification | Meaning |
| --- | --- |
| `good_match` | Most returned articles appear to describe the same issue. |
| `partial_match` | Some useful related articles appear, but important countries or articles are missing. |
| `weak_match` | Results are mostly broad-topic matches rather than the same issue. |
| `no_match` | The API returns no useful related articles. |

## Baseline Result Template

| Article ID | Base country | Language | Category | Keyword | countriesFound | totalArticles | Classification | Notes |
| --- | --- | --- | --- | --- | ---: | ---: | --- | --- |
| TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |

Country breakdown template:

| Article ID | Country | Returned count | Match notes |
| --- | --- | ---: | --- |
| TBD | TBD | TBD | TBD |

## Decision Boundary

This baseline does not introduce Elasticsearch, vector search, RAG, Kafka, or a new schema.
Those options should be considered only after the baseline shows concrete quality gaps that cannot be addressed with small FULLTEXT or keyword improvements.

Potential follow-up decisions:

| Option | Consider when |
| --- | --- |
| FULLTEXT query tuning | Good keywords exist but query shape or ordering loses useful results. |
| Better keyword extraction | Current title tokens are too noisy or miss entities. |
| Multilingual query expansion | Non-English article recall is consistently poor. |
| Vector search or embeddings | Same-issue articles use different wording and FULLTEXT misses them. |
| Issue clustering / `issue_id` | The product needs stable event-level grouping across article ingestion. |
| Elasticsearch | Keyword search, filtering, ranking, and multilingual analysis need a dedicated search layer. |
