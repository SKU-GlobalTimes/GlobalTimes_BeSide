package com.example.globalTimes_be.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Pattern AI_ASK_SSE_PATH = Pattern.compile("^/api/ai/[^/]+/ask/?$");

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtUtil.validate(token)) {
            Long userId = jwtUtil.getUserId(token);
            String email = jwtUtil.getEmail(token);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            authentication.setDetails(email);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("[JWT] 인증 성공 - userId: {}", userId);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        // EventSource가 사용하는 기사 질의 SSE 요청에서만 query token을 허용한다.
        if (isAiAskSseRequest(request)) {
            String queryToken = request.getParameter("token");
            if (StringUtils.hasText(queryToken)) {
                return queryToken;
            }
        }
        return null;
    }

    private boolean isAiAskSseRequest(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod())
                && AI_ASK_SSE_PATH.matcher(request.getRequestURI()).matches();
    }
}
