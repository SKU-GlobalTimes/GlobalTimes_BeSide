package com.example.globalTimes_be.domain.user.controller;

import com.example.globalTimes_be.domain.user.dto.UserResDTO;
import com.example.globalTimes_be.domain.user.entity.User;
import com.example.globalTimes_be.domain.user.repository.UserRepository;
import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import com.example.globalTimes_be.global.apiPayload.code.status.GlobalSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final UserRepository userRepository;

    @Override
    @GetMapping("/me")
    public ResponseEntity<ApiResponse> getMe(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 사용자입니다."));
        return ApiResponse.success(GlobalSuccessStatus._OK.getResponse(), UserResDTO.from(user));
    }
}
