package com.example.globalTimes_be.global.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);
    private final FilterChain filterChain = mock(FilterChain.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void queryTokenAuthenticatesAiAskSseRequest() throws Exception {
        authenticateAs("query-token", 42L);
        MockHttpServletRequest request = request("GET", "/api/ai/7/ask");
        request.setParameter("token", "query-token");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(42L);
        verify(jwtUtil).validate("query-token");
    }

    @Test
    void queryTokenIsIgnoredOutsideAiAskSseRequest() throws Exception {
        MockHttpServletRequest protectedRequest = request("GET", "/api/user/scraps");
        protectedRequest.setParameter("token", "query-token");

        filter.doFilter(protectedRequest, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtUtil, never()).validate("query-token");
    }

    @Test
    void queryTokenIsIgnoredForSummarySseRequest() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/ai/7/summary/sse");
        request.setParameter("token", "query-token");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtUtil, never()).validate("query-token");
    }

    @Test
    void queryTokenIsIgnoredForNonGetAiAskRequest() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/ai/7/ask");
        request.setParameter("token", "query-token");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtUtil, never()).validate("query-token");
    }

    @Test
    void bearerTokenRemainsAvailableOnEveryPathAndTakesPriority() throws Exception {
        authenticateAs("bearer-token", 99L);
        MockHttpServletRequest request = request("GET", "/api/ai/7/ask");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bearer-token");
        request.setParameter("token", "query-token");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(99L);
        verify(jwtUtil).validate("bearer-token");
        verify(jwtUtil, never()).validate("query-token");
    }

    private void authenticateAs(String token, Long userId) {
        when(jwtUtil.validate(token)).thenReturn(true);
        when(jwtUtil.getUserId(token)).thenReturn(userId);
        when(jwtUtil.getEmail(token)).thenReturn("user@example.com");
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
