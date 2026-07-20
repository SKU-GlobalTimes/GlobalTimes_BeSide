package com.example.globalTimes_be.domain.chat.service;

import com.example.globalTimes_be.domain.article.repository.ArticleRepository;
import com.example.globalTimes_be.domain.chat.dto.ChatMessagePair;
import com.example.globalTimes_be.global.redis.RedisUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Testcontainers
class AnonymousChatSessionServiceRedisIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final long TEST_TTL_SECONDS = 60;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(REDIS_PORT);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisUtil redisUtil;
    private static ObjectMapper objectMapper;
    private static AnonymousChatSessionService service;

    @BeforeAll
    static void setUpRedisClient() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisUtil = new RedisUtil(redisTemplate);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new AnonymousChatSessionService(redisUtil, objectMapper, mock(ArticleRepository.class));
        ReflectionTestUtils.setField(service, "ttlSeconds", TEST_TTL_SECONDS);
    }

    @AfterAll
    static void closeRedisClient() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void flushRedis() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    @RepeatedTest(3)
    void listAppendPreservesConcurrentTurnsThatLegacyJsonOverwriteLoses() throws Exception {
        int requests = 20;
        String sessionId = UUID.randomUUID().toString();
        Long articleId = 1L;

        int legacySavedTurns = runLegacyConcurrentOverwrite(sessionId, articleId, requests);
        runConcurrently(requests, index ->
                service.appendTurn(sessionId, articleId, "question-" + index, "answer-" + index, requests));

        List<ChatMessagePair> saved = service.getFullHistory(sessionId, articleId);
        Set<String> expectedQuestions = IntStream.range(0, requests)
                .mapToObj(index -> "question-" + index)
                .collect(Collectors.toSet());

        assertThat(legacySavedTurns).isEqualTo(1);
        assertThat(saved).hasSize(requests);
        assertThat(saved).extracting(ChatMessagePair::question)
                .containsExactlyInAnyOrderElementsOf(expectedQuestions);
    }

    @RepeatedTest(3)
    void sortedSetPreservesEveryArticleUpdatedConcurrentlyByOneSession() throws Exception {
        int articleCount = 20;
        String sessionId = UUID.randomUUID().toString();

        runConcurrently(articleCount, index ->
                service.appendTurn(sessionId, (long) index + 1, "question", "answer", 10));

        List<String> indexedArticleIds = redisUtil.getReverseSortedSetData(indexKey(sessionId)).stream()
                .map(RedisUtil.ScoredValue::value)
                .toList();
        Set<String> expectedArticleIds = IntStream.rangeClosed(1, articleCount)
                .mapToObj(String::valueOf)
                .collect(Collectors.toSet());

        assertThat(indexedArticleIds).hasSize(articleCount);
        assertThat(indexedArticleIds).containsExactlyInAnyOrderElementsOf(expectedArticleIds);
    }

    @Test
    void appendMaintainsMaxTurnsRecentOrderAndTtl() {
        String sessionId = UUID.randomUUID().toString();
        Long articleId = 1L;

        IntStream.range(0, 5).forEach(index ->
                service.appendTurn(sessionId, articleId, "question-" + index, "answer-" + index, 3));

        List<ChatMessagePair> saved = service.getFullHistory(sessionId, articleId);
        Long chatTtl = redisTemplate.getExpire(chatKey(sessionId, articleId), TimeUnit.SECONDS);
        Long indexTtl = redisTemplate.getExpire(indexKey(sessionId), TimeUnit.SECONDS);

        assertThat(saved).extracting(ChatMessagePair::question)
                .containsExactly("question-2", "question-3", "question-4");
        assertThat(chatTtl).isBetween(1L, TEST_TTL_SECONDS);
        assertThat(indexTtl).isBetween(1L, TEST_TTL_SECONDS);
    }

    @Test
    void sortedSetReturnsMostRecentlyUpdatedArticleFirst() throws Exception {
        String sessionId = UUID.randomUUID().toString();

        service.appendTurn(sessionId, 1L, "first", "answer", 3);
        Thread.sleep(20);
        service.appendTurn(sessionId, 2L, "second", "answer", 3);

        assertThat(redisUtil.getReverseSortedSetData(indexKey(sessionId)))
                .extracting(RedisUtil.ScoredValue::value)
                .containsExactly("2", "1");
    }

    @Test
    void sortedSetDoesNotReplaceLatestActivityWithDelayedOlderScore() {
        String key = indexKey(UUID.randomUUID().toString());

        redisUtil.addSortedSetDataIfGreater(key, "1", 200, TEST_TTL_SECONDS);
        redisUtil.addSortedSetDataIfGreater(key, "1", 100, TEST_TTL_SECONDS);

        assertThat(redisUtil.getReverseSortedSetData(key))
                .singleElement()
                .satisfies(value -> {
                    assertThat(value.value()).isEqualTo("1");
                    assertThat(value.score()).isEqualTo(200);
                });
    }

    private int runLegacyConcurrentOverwrite(String sessionId, Long articleId, int requests) throws Exception {
        String key = "chat:anon:" + sessionId + ":" + articleId;
        CountDownLatch allRead = new CountDownLatch(requests);
        runConcurrently(requests, index -> {
            try {
                String stored = redisUtil.getData(key);
                List<ChatMessagePair> history = stored == null
                        ? new ArrayList<>()
                        : objectMapper.readValue(stored, new TypeReference<>() {
                        });
                allRead.countDown();
                if (!allRead.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("legacy readers did not synchronize");
                }
                history.add(new ChatMessagePair("question-" + index, "answer-" + index));
                redisUtil.setData(key, objectMapper.writeValueAsString(history), TEST_TTL_SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        String stored = redisUtil.getData(key);
        List<ChatMessagePair> saved = objectMapper.readValue(stored, new TypeReference<>() {
        });
        return saved.size();
    }

    private void runConcurrently(int tasks, IntConsumer action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks);
        CountDownLatch ready = new CountDownLatch(tasks);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < tasks; index++) {
                int taskIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("workers did not start together");
                    }
                    action.accept(taskIndex);
                    return null;
                }));
            }
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("workers were not ready");
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private String chatKey(String sessionId, Long articleId) {
        return "chat:anon:v2:" + sessionId + ":" + articleId;
    }

    private String indexKey(String sessionId) {
        return "chat:anon:index:v2:" + sessionId;
    }
}
