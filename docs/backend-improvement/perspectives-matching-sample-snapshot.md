# Perspectives matching sample snapshot

## Purpose

This document records a first local snapshot for the Perspectives matching quality baseline.
It follows `perspectives-matching-quality-baseline.md` and uses the local development MySQL data to identify representative samples before introducing heavier search technology.

This snapshot does not change API behavior, DB schema, Redis policy, or search logic.

## Environment

| Item | Value |
| --- | --- |
| Date | 2026-07-03 |
| Dataset | Local development MySQL |
| Article rows | 9853 |
| Query path | MySQL FULLTEXT query shaped like `ArticleRepository.findPerspectives` |
| External translation API | Not called |
| Redis cache | Not used for this snapshot |

Sensitive local `.env` values and external API responses are intentionally excluded.

## Dataset Distribution

Top local article groups:

| Country | Language | Category | Count |
| --- | --- | --- | ---: |
| kr | ko | general | 1088 |
| global | en | general | 742 |
| kr | ko | entertainment | 590 |
| kr | ko | business | 504 |
| kr | ko | sports | 417 |
| cn | zh | general | 394 |
| us | en | sports | 319 |
| us | en | health | 305 |
| gb | en | sports | 284 |
| us | en | technology | 282 |
| de | de | general | 272 |
| jp | ja | sports | 248 |

The dataset is large enough to test English and non-English candidate behavior, but this snapshot is still a local development sample and should not be treated as production quality evidence.

## Method

For each sample, this snapshot uses:

```sql
SELECT *
FROM article
WHERE article_id != :baseArticleId
  AND MATCH(title, description) AGAINST(:keywords IN BOOLEAN MODE)
ORDER BY published_at DESC
LIMIT 50;
```

Keywords were manually derived from the title using the same broad rule as `KeywordExtractor`:

- split by whitespace and punctuation
- remove tokens of length 2 or less
- remove configured English stop words
- keep up to 4 distinct tokens
- make the first 2 tokens required with `+`

For non-English samples, this snapshot records original keyword behavior only.
The full API path may translate non-English plain keywords to English, but that requires external API calls and is intentionally left for a separate measurement.

## Sample Summary

| Base article ID | Country | Language | Category | Keyword used | Match count | Country/language spread | Initial classification | Notes |
| --- | --- | --- | --- | --- | ---: | --- | --- | --- |
| 8146 | gb | en | general | `+First +round Iran talks` | 25 | us/en 14, us/null 5, cn/zh 4, gb/en 2 | `weak_match` | Required terms `First` and `round` produce broad sports and unrelated matches despite an Iran diplomacy base article. |
| 8148 | gb | en | general | `+Russian +troop build threatens` | 0 | none | `no_match` | A Ukraine/Donbas topic returns no matches with the extracted title keywords. |
| 8149 | gb | en | general | `+BTS +fans losing thousands` | 8 | cn/zh 4, gb/en 2, us/en 1, fr/fr 1 | `good_match` | BTS-related results appear across countries/languages. |
| 8147 | gb | en | general | `+Trump +backed political outsider` | 4 | cn/zh 2, us/null 1, gb/en 1 | `partial_match` | Includes a directly related Colombia election article, but also broader Trump/Iran noise. |
| 9638 | cn | zh | general | `+China +defends role global` | 0 | none | `no_match` | English title text exists, but required terms are too specific for current FULLTEXT recall. |
| 8468 | kr | ko | general | `+AI +반도체 붐에` | 0 | none | `no_match` | Korean FULLTEXT behavior and short token handling make original keyword matching ineffective. |
| 8461 | kr | ko | general | `+이란 +원유시장 복귀 합의에` | 0 | none | `no_match` | Korean original keywords do not produce related Iran/oil matches in this query shape. |
| 9440 | fr | fr | general | `+Entre +Meloni Trump divorce` | 0 | none | `no_match` | A general French token becomes required and blocks potentially relevant Trump/Meloni matches. |

## Returned Examples

### 8146: `+First +round Iran talks`

This base article is about US-Iran talks, but the latest returned rows include unrelated sports or broad-topic matches.

| Returned article ID | Country | Language | Title note |
| --- | --- | --- | --- |
| 8413 | gb | en | LPGA play-off |
| 7845 | us | en | USMNT round of 32 price |
| 9744 | cn | zh | China AI rivalry funding |
| 7667 | cn | zh | New Zealand players facing Iran |

This is a useful example where match count is non-zero but issue quality is weak.

### 8149: `+BTS +fans losing thousands`

This sample returns BTS-related results across multiple countries and languages.

| Returned article ID | Country | Language | Title note |
| --- | --- | --- | --- |
| 6014 | cn | zh | BTS fans and Arirang credits |
| 4523 | gb | en | BTS comeback show turnout |
| 4935 | cn | zh | BTS comeback crowd and stock sell-off |
| 3008 | us | en | BTS comeback concert |
| 2104 | fr | fr | BTS album comeback |

This is a useful example where current FULLTEXT behavior can find a coherent topic.

### 8147: `+Trump +backed political outsider`

This sample returns a small mix of related and noisy results.

| Returned article ID | Country | Language | Title note |
| --- | --- | --- | --- |
| 9658 | cn | zh | Trump-backed Colombia presidential election |
| 4941 | cn | zh | Trump and Iran threat |
| 3258 | gb | en | Trump-backed television merger |
| 778 | us | null | Trump allies and Iran |

This is a useful partial-match sample for evaluating future ranking or entity filtering.

## Findings

- Current FULLTEXT count alone is not enough to judge quality.
- Required first tokens can be too generic, for example `First` and `round`.
- English samples can still produce weak matches when title-leading tokens are not core entities.
- BTS is a good positive sample because the extracted keyword is a strong entity.
- Korean and French original keyword samples often return zero in this local query snapshot.
- Some `language='zh'` rows contain English titles, so the `language` field alone does not fully describe search text language.
- The manual keyword examples in this snapshot are measurement aids, not a replacement for the Java policy.
  `KeywordExtractorTest` fixes the current Java behavior before any token policy changes are attempted.
- The regression tests exposed formatting/tokenization quirks, such as compacted `+first+second` BOOLEAN MODE output and Korean tokens joined by the Unicode ellipsis character.
  The compacted BOOLEAN MODE formatting is normalized in #150; token policy changes remain separate follow-up work.

## Java Policy Follow-up

The snapshot table above preserves the original local measurement keywords and match counts.
After #150 and #152, the Java `KeywordExtractor` policy changes the generated keywords for the weakest generic-token samples:

| Base article ID | Previous keyword | #152 Java keyword | Reason |
| --- | --- | --- | --- |
| 8146 | `+First +round Iran talks` | `+Iran +talks ends encouraging` | `First` and `round` are filtered as sample-based generic tokens. |
| 9440 | `+Entre +Meloni Trump divorce` | `+Meloni +Trump divorce italienne` | `Entre` is filtered as a sample-based generic token. |

This section documents generated keyword changes only.
It does not replace the original match counts, because DB-level FULLTEXT result quality should be measured separately after the code change is merged.
If all extracted candidates are removed by the generic-token filter, both `extract()` and `extractPlain()` return an empty string instead of falling back to the original title.

## Follow-up Candidates

1. Measure the full API path for the same samples, including translation behavior, with external API usage explicitly approved.
2. Measure DB-level result changes for #152 generic-token filtering on the representative samples.
3. Add entity-aware keyword extraction only after comparing against the fixed regression tests.
4. Compare the current recency ordering with a relevance-first or hybrid ranking strategy.
5. Define expected countries per sample before trying vector search or Elasticsearch.
6. Keep `issue_id` or clustering as an ADR-level option until concrete sample failures justify the added model.
