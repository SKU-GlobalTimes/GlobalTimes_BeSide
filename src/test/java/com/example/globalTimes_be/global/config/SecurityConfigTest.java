package com.example.globalTimes_be.global.config;

import com.example.globalTimes_be.domain.chat.controller.ChatHistoryController;
import com.example.globalTimes_be.domain.chat.service.ChatHistoryService;
import com.example.globalTimes_be.domain.scrap.controller.ScrapController;
import com.example.globalTimes_be.domain.scrap.service.ScrapService;
import com.example.globalTimes_be.global.security.JwtUtil;
import com.example.globalTimes_be.global.security.oauth2.CustomOAuth2UserService;
import com.example.globalTimes_be.global.security.oauth2.OAuth2SuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ScrapController.class, ChatHistoryController.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;
    @MockBean
    private ScrapService scrapService;
    @MockBean
    private ChatHistoryService chatHistoryService;

    @Test
    void protectedUserApiRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/user/scraps"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/articles/1/chat-history"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/articles/1/scrap/status"))
                .andExpect(status().isUnauthorized());

        verify(scrapService, never()).getScrapList(org.mockito.ArgumentMatchers.anyLong());
        verify(chatHistoryService, never()).getChatsByArticle(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        verify(scrapService, never()).isScrapped(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void protectedScrapApiRejectsInvalidJwt() throws Exception {
        when(jwtUtil.validate("invalid-token")).thenReturn(false);

        mockMvc.perform(post("/api/articles/1/scrap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        verify(scrapService, never()).toggle(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void validJwtPrincipalOwnsScrapAndChatQueries() throws Exception {
        authenticateAs(42L);
        when(scrapService.getScrapList(42L)).thenReturn(List.of());
        when(chatHistoryService.getChatsByArticle(42L, 7L)).thenReturn(List.of());

        mockMvc.perform(get("/api/user/scraps")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/articles/7/chat-history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk());

        verify(scrapService).getScrapList(42L);
        verify(chatHistoryService).getChatsByArticle(42L, 7L);
    }

    @Test
    void publicScrapLookupRemainsAccessibleWithoutJwt() throws Exception {
        when(scrapService.getScrap(List.of(1L))).thenReturn(List.of());

        mockMvc.perform(get("/api/scrap").param("id", "1"))
                .andExpect(status().isOk());

        verify(scrapService).getScrap(List.of(1L));
    }

    private void authenticateAs(Long userId) {
        when(jwtUtil.validate("valid-token")).thenReturn(true);
        when(jwtUtil.getUserId("valid-token")).thenReturn(userId);
        when(jwtUtil.getEmail("valid-token")).thenReturn("user@example.com");
    }
}
