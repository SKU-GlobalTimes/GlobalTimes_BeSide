# Perspectives FULLTEXT ordering comparison

## Purpose

This document compares ordering strategies for `ArticleRepository.findPerspectives`.

#156 showed that #152 improved generated keyword quality for weak generic-token samples.
However, sample 8146 still had nearby Middle East diplomacy noise near the top because current results are ordered by recency:

```sql
ORDER BY published_at DESC
```

This measurement checks whether the remaining noise is better explained as an ordering problem.
It does not change API behavior, DB schema, Redis policy, crawler/RSS feeds, or `KeywordExtractor`.

Interpret this result together with `perspectives-source-coverage-limit-log.md`.
FULLTEXT can only rank articles that exist in the collected local dataset.

## Environment

| Item | Value |
| --- | --- |
| Date | 2026-07-08 |
| Dataset | Local development MySQL via Docker |
| MySQL | 8.0.45 |
| Article rows | 9853 |
| FULLTEXT index | `ft_article_title_description(title, description)` |
| Query path | DB-level query shaped like `ArticleRepository.findPerspectives` |
| External translation API | Not called |
| Redis cache | Not used |

Sensitive local `.env` values and external API responses are intentionally excluded.

## Compared Ordering Strategies

All strategies use the same candidate predicate:

```sql
WHERE article_id != :baseArticleId
  AND MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE)
LIMIT 50
```

### Latest-first

Current production ordering:

```sql
ORDER BY published_at DESC
```

Meaning: prefer the newest matching articles.

### Relevance-first

FULLTEXT score ordering:

```sql
ORDER BY MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE) DESC,
         article_id DESC
```

Meaning: prefer articles with stronger FULLTEXT keyword overlap.

### Hybrid candidate

Exploratory score plus bounded recency boost:

```sql
hybrid_score =
  fulltext_score
  + GREATEST(
      0,
      1 - TIMESTAMPDIFF(HOUR, published_at, max_matched_published_at) / (24 * 30)
    ) * 5

ORDER BY hybrid_score DESC,
         published_at DESC
```

Meaning: prefer relevant articles, but give a limited boost to articles within 30 days of the newest matched article.
This is a measurement candidate, not a final ranking policy.

## Sample Summary

Because all selected samples have fewer than 50 matches, each ordering strategy returns the same candidate set.
This measurement therefore compares top order, not recall.

| Base article ID | Keyword | Match count | Country/language spread |
| --- | --- | ---: | --- |
| 8146 | `+Iran +talks ends encouraging` | 27 | global/en 13, cn/zh 7, gb/en 3, us/en 2, de/en 1, global/null 1 |
| 9440 | `+Meloni +Trump divorce italienne` | 3 | global/en 2, de/de 1 |
| 8147 | `+Trump +backed political outsider` | 4 | cn/zh 2, gb/en 1, us/null 1 |
| 8149 | `+BTS +fans losing thousands` | 8 | cn/zh 4, gb/en 2, fr/fr 1, us/en 1 |

## 8146: US-Iran Talks

Base title:

```text
First round of US-Iran talks ends with 'encouraging progress', mediators say
```

### Top Results

| Ordering | Top returned examples |
| --- | --- |
| latest-first | 9652 Blockade/assets to Iran in Swiss talks; 9667 Iran-US talks continue; 8086 Iran war live and Lebanon; 8091 US-Iran talks to kick off; 7939 US-Iran talks begin |
| relevance-first | 9667 Iran-US talks continue; 9652 Blockade/assets to Iran; 5219 Iran denies US talks; 1517 Ukraine peace talks amid Iran war; 4276 Iran World Cup |
| hybrid | 9652 Blockade/assets to Iran; 9667 Iran-US talks continue; 8114 US envoy headed for Switzerland; 8129 Israel-Lebanon talks; 8086 Iran war live and Lebanon |

### Interpretation

- Latest-first keeps the newest Iran/US talks articles high, but also keeps recent Lebanon/ceasefire regional noise near the top.
- Relevance-first raises strong `Iran`/`talks` overlaps, but it can promote older or broader matches such as Ukraine peace talks or Iran World Cup.
- The hybrid candidate reduces the pure relevance-first drift toward older March articles while still using score to avoid pure recency ordering.
- Remaining Lebanon/ceasefire results are not fully solved by ordering because the text overlaps with the same regional diplomacy context.

## 9440: Meloni/Trump French Sample

Base title:

```text
Entre Meloni et Trump, divorce à l'italienne
```

### Top Results

| Ordering | Top returned examples |
| --- | --- |
| latest-first | 8096 Trump/Meloni photos; 8134 Meloni says Trump fabricated story; 9517 German Meloni/Trump article |
| relevance-first | 9517 German Meloni/Trump article; 8134 Meloni says Trump fabricated story; 8096 Trump/Meloni photos |
| hybrid | 9517 German Meloni/Trump article; 8134 Meloni says Trump fabricated story; 8096 Trump/Meloni photos |

### Interpretation

- The candidate set is only 3 rows, so ordering cannot solve source coverage limits.
- Relevance-first and hybrid both move the highest FULLTEXT score article to the top.
- This sample is useful for recall tracking after #152, but it is too small to justify a ranking policy alone.

## 8147: Trump-backed Political Outsider

Base title note:

```text
Trump-backed political outsider
```

### Top Results

| Ordering | Top returned examples |
| --- | --- |
| latest-first | 9658 Trump-backed Colombia election; 4941 Iran awaits Trump threat; 3258 Trump-backed television merger; 778 Trump allies/Iran |
| relevance-first | 4941 Iran awaits Trump threat; 9658 Trump-backed Colombia election; 778 Trump allies/Iran; 3258 Trump-backed television merger |
| hybrid | 9658 Trump-backed Colombia election; 4941 Iran awaits Trump threat; 778 Trump allies/Iran; 3258 Trump-backed television merger |

### Interpretation

- Latest-first already puts the most likely same-issue article first because it is much newer.
- Relevance-first over-promotes a higher-score but wrong-context Iran/Trump article.
- Hybrid keeps the Colombia election article first while still preserving score as a ranking signal.
- This sample argues against switching directly to pure relevance-first.

## 8149: BTS Fans

Base title note:

```text
BTS fans losing thousands
```

### Top Results

| Ordering | Top returned examples |
| --- | --- |
| latest-first | 6014 BTS fans/Arirang credits; 4523 BTS agency shares; 4935 BTS comeback crowd; 3246 K-pop fans in Seoul; 3008 BTS comeback concert |
| relevance-first | 3008 BTS comeback concert; 6014 BTS fans/Arirang credits; 3246 K-pop fans in Seoul; 2874 BTS first show; 4935 BTS comeback crowd |
| hybrid | 3008 BTS comeback concert; 6014 BTS fans/Arirang credits; 3246 K-pop fans in Seoul; 2874 BTS first show; 4935 BTS comeback crowd |

### Interpretation

- This is a good-match control sample: most returned rows are BTS-related under all strategies.
- Relevance-first and hybrid surface stronger BTS event matches than latest-first.
- The result suggests ordering can improve top position quality when the candidate set is already coherent.

## Findings

- Pure latest-first is simple and freshness-friendly, but it can keep nearby topical noise high.
- Pure relevance-first can improve exact keyword overlap, but it may over-promote older or wrong-context articles with strong token overlap.
- The measured hybrid candidate looks safer than pure relevance-first for samples 8146 and 8147 because it keeps recency as a bounded signal.
- Since all measured candidate sets are under 50 rows, this comparison does not evaluate recall. It evaluates top ordering only.
- Source coverage remains a hard boundary. If the collected DB lacks same-issue articles, no ordering policy can recover them.

## Recommendation

Do not directly replace `ORDER BY published_at DESC` with pure relevance-first.

If code changes are attempted later, prefer a small, explicit hybrid experiment with tests and before/after measurement:

1. Select representative samples and expected top-result intent.
2. Add a repository method or query variant for hybrid ranking.
3. Compare API response order against this DB-level baseline.
4. Keep source coverage limitations documented separately from ranking failures.
