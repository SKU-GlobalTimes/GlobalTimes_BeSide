# Perspectives Multilingual FULLTEXT Integration

## Purpose

This verification fixes the backend behavior of the controlled non-English Perspectives search path:

```text
non-English base article
-> keyword extraction
-> mocked English translation
-> real MySQL FULLTEXT search
-> optional original-keyword search
-> deduplication and country grouping
```

It does not measure semantic similarity or improve matching quality. Its purpose is to distinguish deterministic backend-path failures from conditions where a related article is absent or the supplied translation keyword is wrong.

## Test Boundary

| Component | Test behavior |
| --- | --- |
| `PerspectivesService` | Real service implementation |
| `KeywordExtractor` | Real keyword extraction |
| `ArticleRepository.findPerspectives` | Real native query |
| Database | MySQL 8 Testcontainers with Flyway FULLTEXT schema |
| Translation | Mockito-controlled result or exception |
| Redis | Cache TTL set to zero; no read or write |
| Article data | Test-only Source and Article fixtures |

The test does not call Google Translation, News API, RSS, the compose database, or any deployed service.

## Controlled Scenarios

| Scenario | Related row in MySQL | Translation condition | Expected result |
| --- | --- | --- | --- |
| translated success | present | `Lunaris Summit Joint Statement` | English related article returned |
| source coverage absence | absent | same correct translation | zero articles |
| wrong translation | present | `Football Transfer Market Opens` | zero articles |
| translation failure | French related row present | translation exception | original French FULLTEXT result returned |
| translated/original overlap | one row matches both keyword sets | valid translated keyword | one final article, not two |

The absence and wrong-translation scenarios intentionally produce the same zero-result response under different controlled inputs. This shows why a real zero-result response cannot be assigned to FULLTEXT, translation, or source coverage without additional evidence.

## Fixture Interpretation

The fixture text is written to make each expected FULLTEXT boundary deterministic. It is not a sample of real editorial content and is not evidence that the same query will find semantically related articles in the collected dataset.

The French fallback fixture uses whitespace-separated Latin tokens so the test focuses on fallback orchestration rather than introducing Korean tokenizer behavior into the same assertion.

Fixtures are committed before the service query runs. The service call runs in a transaction so the native query and lazy `Source` access use the real persistence path.

## Claims And Non-Claims

This verification supports the following claims:

- non-English inputs request translation
- a supplied translated keyword reaches MySQL BOOLEAN MODE FULLTEXT
- translation failure retains the original-keyword fallback
- translated and original query results are merged without duplicate article IDs
- country grouping and total counts reflect the merged result

It does not support these claims:

- Google Translation produces an accurate keyword
- configured RSS or News API sources contain the related event
- returned articles describe the same real-world event
- multilingual recall, precision, or semantic similarity improved
- Elasticsearch, embeddings, Vector DB, or translation persistence is required

Real matching quality requires a separately labeled sample containing expected related article IDs. Source freshness and local coverage must also be recorded before interpreting missing results.
