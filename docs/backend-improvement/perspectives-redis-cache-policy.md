# Perspectives Redis cache policy

## Purpose

`GET /api/news/{id}/perspectives` groups related articles by country after keyword extraction, optional translation, and MySQL FULLTEXT search.
Because this path is more expensive than a simple article lookup, `PerspectivesService` stores the computed response in Redis.

This document records the current cache policy, failure behavior, and stale data trade-off so later performance work can change it deliberately.

## Terms

| Term | Meaning in this project |
| --- | --- |
| Cache deletion | Directly removing a Redis key, for example deleting `perspectives:article:8449`. This is useful for tests or manual cleanup. |
| Cache invalidation | Removing or refreshing cache automatically when source data changes in a way that makes the cached value outdated. |
| Stale cache | A cached response that is still served even though the underlying DB data changed after it was cached. |
| Stale allowance | A deliberate decision to tolerate stale cache for a bounded time, usually controlled by TTL, because the data is read-heavy and not mission critical. |

## Current policy

| Item | Current behavior |
| --- | --- |
| Key prefix | `perspectives:article:` |
| Key shape | `perspectives:article:{articleId}` |
| Value | Serialized `PerspectivesResDTO` JSON |
| TTL property | `perspectives.cache-ttl-seconds` |
| Default TTL | `3600` seconds |
| Cache disabled condition | `perspectives.cache-ttl-seconds=0` |
| Explicit invalidation | Not implemented |

Configuration:

```yaml
perspectives:
  cache-ttl-seconds: 3600
```

## Runtime flow

### Cache hit

1. Read `perspectives:article:{articleId}` from Redis.
2. Deserialize JSON into `PerspectivesResDTO`.
3. Return the cached response.
4. Log `cacheHit=true`, `cacheReadMs`, `totalMs`, `countriesFound`, and `totalArticles`.

### Cache miss

1. Load base article from MySQL.
2. Extract plain and FULLTEXT boolean keywords from the title.
3. Translate non-English article keywords to English.
4. Search related articles with MySQL FULLTEXT.
5. Group results by country and limit each country to 3 articles.
6. Serialize the response and store it in Redis with TTL.
7. Log `cacheHit=false` and step-level timings.

## Failure behavior

| Failure case | Current behavior | API impact |
| --- | --- | --- |
| Redis read failure | Warn and recompute from DB/FULLTEXT | Response can still succeed |
| Cache JSON deserialize failure | Warn and recompute from DB/FULLTEXT | Response can still succeed |
| Redis write failure | Warn and ignore | Response succeeds, next request may be cold again |
| Base article missing | Throw domain exception | Response fails as before |

Redis is treated as an optimization layer, not the source of truth.
The source of truth remains MySQL.

## Stale cache analysis

The current service is mostly read-heavy after news ingestion.
For this reason, bounded stale cache is acceptable for Perspectives responses as long as TTL remains short enough for the product expectation.

| Data change | Can affect Perspectives response? | Current handling | Notes |
| --- | --- | --- | --- |
| New related articles inserted | Yes | Existing cache remains until TTL expires | Newly inserted related articles may not appear immediately. |
| Base article title changed | Yes | Existing cache remains until TTL expires | Keyword extraction result can become outdated. |
| Article country/category changed | Yes | Existing cache remains until TTL expires | Country grouping can become outdated. |
| Article source changed | Maybe | Existing cache remains until TTL expires | Source name is included in child article DTOs. |
| View count increased | No | No invalidation needed | View count is not part of Perspectives response. |
| Summary updated | No | No invalidation needed | Summary is not part of Perspectives response. |
| Crawled content updated | No | No invalidation needed | Crawled content is not part of Perspectives response. |
| Scrap/chat data changed | No | No invalidation needed | Not used by Perspectives response. |

## Decision

For the current project stage, keep TTL-based stale allowance instead of adding explicit invalidation.

Reasons:

- Perspectives is a read-heavy derived response.
- Article edits that affect Perspectives appear rare in the current API surface.
- News ingestion can add related articles, but showing them after at most the TTL window is acceptable for this feature.
- Explicit invalidation would require wiring cache deletion into every write path that can affect related article matching.
- The current 1-hour TTL bounds stale responses while preserving the measured warm cache benefit.

## When to add invalidation

Add explicit invalidation if one of these becomes true:

- Admin APIs start editing article title, country, category, source, or language.
- News ingestion becomes frequent enough that newly inserted related articles must appear immediately.
- Users report stale Perspectives results as a correctness issue.
- Cache TTL needs to be raised significantly beyond the current 1-hour window.

Potential invalidation targets:

```text
perspectives:article:{baseArticleId}
```

Full correctness is harder than deleting only the changed article key.
If a newly inserted article is related to many existing base articles, those existing base article caches can also become stale.
That broader invalidation problem should be handled in a separate design issue.

## Follow-up candidates

- Measure cold cache with multiple article IDs to understand FULLTEXT variance.
- Use MySQL `EXPLAIN` on `ArticleRepository.findPerspectives`.
- Revisit TTL after measuring warm cache hit ratio and stale tolerance.
- Consider local cache only if Redis hit path itself becomes a bottleneck.
- Consider asynchronous precomputation only if cold cache latency becomes user-visible at higher traffic.
