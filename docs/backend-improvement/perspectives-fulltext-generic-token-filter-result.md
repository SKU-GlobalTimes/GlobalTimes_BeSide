# Perspectives FULLTEXT generic-token filter result

## Purpose

This document records the DB-level FULLTEXT result change after #152.

#152 changed the Java `KeywordExtractor` policy by filtering sample-based generic tokens:

- `first`
- `round`
- `entre`

This measurement checks whether the generated keyword improvement also changes the local MySQL FULLTEXT candidates.
It does not change API behavior, DB schema, Redis policy, crawler/RSS feeds, ranking, or the FULLTEXT query shape.

Interpret this result together with `perspectives-source-coverage-limit-log.md`.
The collected source data limits what FULLTEXT can return.

## Environment

| Item | Value |
| --- | --- |
| Date | 2026-07-04 |
| Dataset | Local development MySQL via `docker-compose.dev.yml` |
| Article rows | 9853 |
| FULLTEXT index | `ft_article_title_description(title, description)` |
| Query path | MySQL FULLTEXT query shaped like `ArticleRepository.findPerspectives` |
| External translation API | Not called |
| Redis cache | Not used |

Sensitive local `.env` values and external API responses are intentionally excluded.

## Query Shape

```sql
SELECT *
FROM article
WHERE article_id != :baseArticleId
  AND MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE)
ORDER BY published_at DESC
LIMIT 50;
```

For this measurement, `match count` means the number of rows matching the FULLTEXT predicate before the `LIMIT 50`.
Country/language spread and returned examples use the same latest-first ordering as `findPerspectives`.

## Summary

| Base article ID | Base title note | Before keyword | Before count | After keyword | After count | Initial interpretation |
| --- | --- | --- | ---: | --- | ---: | --- |
| 8146 | US-Iran talks | `+First +round Iran talks` | 25 | `+Iran +talks ends encouraging` | 27 | Improved: top results move from sports/broad `round` noise to Iran/US talks. |
| 9440 | Meloni/Trump French sample | `+Entre +Meloni Trump divorce` | 0 | `+Meloni +Trump divorce italienne` | 3 | Improved recall, but still limited by available source coverage. |

## 8146: US-Iran Talks

Base title:

```text
First round of US-Iran talks ends with 'encouraging progress', mediators say
```

### Before #152

Keyword:

```text
+First +round Iran talks
```

Country/language spread:

| Country | Language | Count |
| --- | --- | ---: |
| us | en | 14 |
| us | null | 5 |
| cn | zh | 4 |
| gb | en | 2 |

Top returned examples:

| Article ID | Country | Language | Title note |
| --- | --- | --- | --- |
| 8413 | gb | en | Yamashita denies Woad third LPGA title in play-off |
| 7845 | us | en | USMNT Round of 32 match price |
| 9744 | cn | zh | China AI rivalry funding |
| 6566 | gb | en | PGA Tour round |
| 6247 | us | en | OSU stars and 1st round |
| 7667 | cn | zh | New Zealand players facing Iran |
| 6100 | cn | zh | Miami Open |
| 4427 | us | en | March Madness second round |

Interpretation:

- `First` and `round` required broad sports and tournament-style matches.
- The match count was non-zero, but the first returned examples were mostly weak matches.
- This looked like a search-side keyword problem rather than only source coverage.

### After #152

Keyword:

```text
+Iran +talks ends encouraging
```

Country/language spread:

| Country | Language | Count |
| --- | --- | ---: |
| global | en | 13 |
| cn | zh | 7 |
| gb | en | 3 |
| us | en | 2 |
| de | en | 1 |
| global | null | 1 |

Top returned examples:

| Article ID | Country | Language | Title note |
| --- | --- | --- | --- |
| 9652 | cn | zh | Blockade lifted, assets returned to Iran in Swiss talks |
| 9667 | cn | zh | Iran-US talks continue |
| 8086 | global | en | Iran war live and Switzerland |
| 8091 | global | en | US-Iran talks to kick off Sunday |
| 7939 | global | en | US-Iran talks begin in Switzerland |
| 8114 | global | en | US envoy headed for Switzerland |
| 8129 | global | en | Israel-Lebanon talks in Washington |
| 8136 | global | en | Israel attacks Lebanon despite ceasefire |

Interpretation:

- The top results became much more centered on Iran/US talks and nearby Middle East diplomacy.
- Some broader regional diplomacy/noise remains, which is expected because `ORDER BY published_at DESC` is still recency-based, not relevance-based.
- This supports #152 as a useful keyword-side improvement for this sample.

## 9440: Meloni/Trump French Sample

Base title:

```text
Entre Meloni et Trump, divorce à l'italienne
```

### Before #152

Keyword:

```text
+Entre +Meloni Trump divorce
```

Result:

| Metric | Value |
| --- | ---: |
| Match count | 0 |

Interpretation:

- `Entre` as a required token blocked recall for this sample.
- Because the result was zero, we cannot tell from this query alone how much of the failure came from keyword choice versus source coverage.

### After #152

Keyword:

```text
+Meloni +Trump divorce italienne
```

Country/language spread:

| Country | Language | Count |
| --- | --- | ---: |
| global | en | 2 |
| de | de | 1 |

Top returned examples:

| Article ID | Country | Language | Title note |
| --- | --- | --- | --- |
| 8096 | global | en | Trump says Italy's Meloni sought photos with him |
| 8134 | global | en | Italy diplomat and Meloni/Trump fabricated story |
| 9517 | de | de | German article about Meloni and Trump |

Interpretation:

- Filtering `Entre` produced a small but concrete recall improvement: `0` to `3`.
- The available matches are related to Meloni/Trump, but the result set remains small.
- Per `perspectives-source-coverage-limit-log.md`, this should be interpreted as mixed: keyword-side improvement plus likely source coverage/data availability limits.

## Result Interpretation

Observed improvement:

- #152 improved generated keyword focus for the weak generic-token samples.
- The DB-level result for 8146 moved away from sports/tournament noise and toward Iran/US talks.
- The DB-level result for 9440 changed from no match to a small Meloni/Trump candidate set.

Remaining limits:

- Result ordering is still latest-first, not relevance-first.
- FULLTEXT still requires enough textual overlap in collected `title`/`description`.
- Missing or delayed RSS/News API source data cannot be recovered by keyword tuning.
- The measurement does not call the full API path, translation API, or Redis.

## Follow-Up Candidates

1. Compare latest-first ordering with relevance-first or hybrid ordering for the same samples.
2. Measure the full API path with translation enabled for non-English base articles, with external API usage explicitly approved.
3. Add source coverage/freshness metrics for representative issues before choosing Elasticsearch, vector search, or issue clustering.
4. Expand generic-token filtering only with additional measured samples, not as a broad stop-word list by intuition.
