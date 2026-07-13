package com.example.globalTimes_be.domain.ai.controller;

import com.example.globalTimes_be.domain.ai.service.AiService;
import com.example.globalTimes_be.domain.ai.service.AiSseService;
import com.example.globalTimes_be.domain.chat.service.AnonymousChatSessionService;
import com.example.globalTimes_be.domain.detail.service.DetailService;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.async.WebAsyncTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock
    private AiService aiService;
    @Mock
    private AiSseService aiSseService;
    @Mock
    private DetailService detailService;
    @Mock
    private AnonymousChatSessionService anonymousChatSessionService;

    private AiController aiController;

    @BeforeEach
    void setUp() {
        aiController = new AiController(
                aiService,
                aiSseService,
                detailService,
                anonymousChatSessionService,
                new SimpleAsyncTaskExecutor()
        );
        ReflectionTestUtils.setField(aiController, "summarySaveEnabled", true);
        ReflectionTestUtils.setField(aiController, "summaryAsyncTimeoutMs", 15000L);
    }

    @Test
    void summarizeArticleDefersWorkToAsyncTask() throws Exception {
        when(detailService.getArticleSummary(1L, "English")).thenReturn("saved summary");

        WebAsyncTask<ResponseEntity<ApiResponse>> task = aiController.summarizeArticle(1L, "English");

        verify(detailService, never()).getArticleSummary(1L, "English");

        Object result = task.getCallable().call();

        assertThat(result).isInstanceOf(ResponseEntity.class);
        assertThat(((ResponseEntity<?>) result).getStatusCode().is2xxSuccessful()).isTrue();
        verify(detailService).getArticleSummary(1L, "English");
        verify(aiService, never()).summarizeArticle(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
