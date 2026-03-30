package com.example.globalTimes_be.domain.user.controller;

import com.example.globalTimes_be.global.apiPayload.code.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "Auth", description = "OAuth2 로그인 및 사용자 정보 API")
public interface AuthControllerDocs {

    @Operation(
            summary = "내 정보 조회",
            description = "JWT 토큰으로 로그인한 사용자의 정보를 조회합니다. Authorization 헤더에 `Bearer {token}`을 포함해야 합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "message": "성공",
                              "data": {
                                "userId": 1,
                                "email": "user@example.com",
                                "nickname": "홍길동",
                                "provider": "kakao"
                              }
                            }
                            """))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    ResponseEntity<ApiResponse> getMe(
            @Parameter(hidden = true) Authentication authentication
    );
}
