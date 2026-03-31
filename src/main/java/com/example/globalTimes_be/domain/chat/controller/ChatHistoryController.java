package com.example.globalTimes_be.domain.chat.controller;

import com.example.globalTimes_be.domain.chat.service.ChatHistoryService;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatHistoryController implements ChatHistoryControllerDocs {

    private final ChatHistoryService chatHistoryService;

    // GET /api/user/chat-history  →  팝업 목록 (기사별 마지막 대화 미리보기)
    @Override
    @GetMapping("/user/chat-history")
    public ResponseEntity<ApiResponse> getChatList(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(
                GlobalSuccessStatus._OK.getResponse(),
                chatHistoryService.getChatList(userId)
        );
    }

    // GET /api/articles/{id}/chat-history  →  기사 상세 페이지 전체 대화 내역
    @Override
    @GetMapping("/articles/{id}/chat-history")
    public ResponseEntity<ApiResponse> getChatsByArticle(
            @PathVariable("id") Long articleId,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(
                GlobalSuccessStatus._OK.getResponse(),
                chatHistoryService.getChatsByArticle(userId, articleId)
        );
    }
}
