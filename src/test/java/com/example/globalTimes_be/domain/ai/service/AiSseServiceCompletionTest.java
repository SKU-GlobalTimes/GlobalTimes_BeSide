package com.example.globalTimes_be.domain.ai.service;

import com.example.globalTimes_be.domain.chat.service.AnonymousChatSessionService;
import com.example.globalTimes_be.domain.chat.service.ChatHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class AiSseServiceCompletionTest {

    private ChatHistoryService chatHistoryService;
    private AnonymousChatSessionService anonymousChatSessionService;
    private AiSseService aiSseService;
    private SseEmitter emitter;

    @BeforeEach
    void setUp() {
        chatHistoryService = mock(ChatHistoryService.class);
        anonymousChatSessionService = mock(AnonymousChatSessionService.class);
        aiSseService = new AiSseService(
                mock(WebClient.class),
                chatHistoryService,
                anonymousChatSessionService
        );
        ReflectionTestUtils.setField(aiSseService, "contextWindowSize", 10);
        emitter = mock(SseEmitter.class);
    }

    @Test
    void completesLoginStreamAfterHistorySave() {
        aiSseService.completeStreaming(emitter, "question", 1L, 2L, null, "answer");

        verify(chatHistoryService).save(1L, 2L, "question", "answer");
        verify(emitter).complete();
        verify(emitter, never()).completeWithError(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void historySaveFailureIsLoggedAndDoesNotFailCompletedAnswer(CapturedOutput output) {
        doThrow(new DataIntegrityViolationException("commit failed"))
                .when(chatHistoryService)
                .save(1L, 2L, "question", "answer");

        assertThatCode(() ->
                aiSseService.completeStreaming(emitter, "question", 1L, 2L, null, "answer")
        ).doesNotThrowAnyException();

        verify(emitter).complete();
        verify(emitter, never()).completeWithError(org.mockito.ArgumentMatchers.any());
        assertThat(output)
                .contains("채팅 이력 저장 실패")
                .contains("userId=1")
                .contains("articleId=2")
                .contains("DataIntegrityViolationException");
    }

    @Test
    void anonymousCompletionKeepsExistingRedisAppendPath() {
        aiSseService.completeStreaming(
                emitter,
                "question",
                null,
                2L,
                "anonymous-session",
                "answer"
        );

        verify(anonymousChatSessionService)
                .appendTurn("anonymous-session", 2L, "question", "answer", 10);
        verify(chatHistoryService, never())
                .save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(emitter).complete();
    }

    @Test
    void mockGeminiCompletionDelegatesLoginHistorySave() {
        WebClient mockGemini = WebClient.builder()
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
                                .body("""
                                        data: {"candidates":[{"content":{"parts":[{"text":"mock answer"}]}}]}

                                        """)
                                .build()
                ))
                .build();
        AiSseService streamingService = new AiSseService(
                mockGemini,
                chatHistoryService,
                anonymousChatSessionService
        );
        ReflectionTestUtils.setField(streamingService, "geminiApiKey", "test-api-key");
        ReflectionTestUtils.setField(streamingService, "contextWindowSize", 10);
        when(chatHistoryService.getRecentContext(1L, 2L, 10)).thenReturn(List.of());

        assertThatCode(() ->
                streamingService.askGPT("article content", "question", 1L, 2L, null)
        ).doesNotThrowAnyException();

        verify(chatHistoryService).save(1L, 2L, "question", "mock answer");
    }
}
