# Perspectives source coverage limit log

## Purpose

This log records a data-side limitation that must be considered when interpreting Perspectives matching quality.

Perspectives matching is affected not only by keyword extraction, MySQL FULLTEXT, translation, ranking, or future semantic similarity.
It is also constrained by the article data that has actually been collected from RSS and News API sources.

This document is a measurement and ADR interpretation aid.
It does not change crawler behavior, RSS feeds, DB schema, FULLTEXT query shape, ranking, or cache policy.

## Background

The project collects articles from multiple external sources.
Those sources do not behave like a balanced event dataset:

- each RSS feed can have a different update interval
- each media source can choose different stories for its RSS feed
- some countries, languages, or categories can have more collected articles than others
- a same global issue can appear in one source earlier than another
- some sources may publish a related article outside the categories or feeds currently collected
- News API free-plan limitations and RSS feed coverage affect which articles enter the DB

Therefore, Perspectives quality is bounded by both search quality and data coverage.

## Interpretation Rule

When measuring FULLTEXT, keyword, vector, embedding, or future issue-clustering quality, separate these two causes:

| Observation | Search-side interpretation | Data-side interpretation |
| --- | --- | --- |
| `0` matches | Query terms, language, tokenization, or ranking may be poor. | Related articles may not exist in the collected DB yet. |
| Low country diversity | Matching may over-focus on one language or source. | Some country feeds may not have supplied the issue. |
| No related non-English result | Translation or multilingual search may be insufficient. | Non-English sources may not have published or been collected for that issue. |
| Noisy matches | Keywords may be too broad or ordering may be recency-biased. | The DB may contain many broad-topic articles but few exact same-issue articles. |
| Apparent improvement after query tuning | Search terms may be better. | The sample may simply have better available coverage than other issues. |

## FULLTEXT-Specific Limit

MySQL FULLTEXT can only search text that exists in the local `article` table.
It cannot find:

- articles that have not been collected yet
- articles from feeds that do not expose the issue
- articles from sources outside the configured RSS/News API list
- same-issue articles written with words that do not overlap enough with the generated query

For this reason, a FULLTEXT result change measurement should not conclude "the algorithm is bad" or "the algorithm is fixed" from match count alone.
It should record returned examples and distinguish search failure from possible source coverage gaps.

## ADR Implication

Future ADRs for Elasticsearch, vector search, embeddings, RAG, or issue clustering should use this boundary:

| Candidate | Can help with | Cannot solve by itself |
| --- | --- | --- |
| FULLTEXT tuning | Token/query shape, index use, simple keyword recall | Missing articles or uncollected sources |
| Better keyword extraction | Noisy title tokens and weak required terms | Source update delay or feed selection bias |
| Elasticsearch | Search analyzers, ranking, multilingual text handling | Lack of collected same-issue articles |
| Vector/embedding search | Different wording for the same issue | Articles absent from the DB |
| Issue clustering | Stable grouping of collected articles | External source coverage and freshness |
| RAG | Explanation over retrieved content | Retrieval gaps caused by missing source data |

If a representative issue is absent from the collected dataset, search-layer technology cannot recover it.
The correct follow-up may be source coverage analysis, ingestion scheduling, feed expansion, or data freshness tracking rather than search ranking.

## Measurement Guidance

For every future Perspectives quality snapshot, record:

- measurement date and local dataset size
- sample article ID, country, language, category, source, and published time when available
- generated keyword before and after the change
- match count and returned country/language spread
- a few returned title notes for manual quality review
- whether `0` or low-diversity results may be caused by source coverage rather than search behavior

Avoid using match count alone as the success metric.
Prefer a short interpretation that says whether the result looks like:

- search/query limitation
- data coverage limitation
- mixed limitation
- insufficient evidence

## Link To Current P3 Flow

Current P3 work has already created:

- a matching quality baseline
- a local FULLTEXT sample snapshot
- `KeywordExtractor` regression tests
- BOOLEAN MODE formatting normalization
- sample-based generic-token filtering

The next DB-level FULLTEXT result measurement should use this log as an interpretation guardrail before deciding whether keyword tuning is enough or whether an ADR is needed.
