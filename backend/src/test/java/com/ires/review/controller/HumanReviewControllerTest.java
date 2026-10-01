package com.ires.review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ires.auth.security.CustomUserDetailsService;
import com.ires.auth.security.JwtAuthenticationFilter;
import com.ires.auth.security.JwtService;
import com.ires.common.exception.GlobalExceptionHandler;
import com.ires.config.SecurityConfig;
import com.ires.review.dto.HumanReviewRequest;
import com.ires.review.entity.ReviewAction;
import com.ires.review.entity.ReviewArtifactType;
import com.ires.review.service.HumanReviewService;
import com.ires.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HumanReviewController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class HumanReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HumanReviewService humanReviewService;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void letRequestsPassThroughMockedJwtFilter() throws Exception {
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void requiresAuthenticationToReviewArtifact() throws Exception {
        mockMvc.perform(post("/api/v1/ai-artifacts/USER_STORY/" + UUID.randomUUID() + "/review"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void businessAnalystCanAcceptPendingArtifact() throws Exception {
        UUID artifactId = UUID.randomUUID();
        HumanReviewRequest request = new HumanReviewRequest(ReviewAction.ACCEPT, null, null);

        mockMvc.perform(post("/api/v1/ai-artifacts/USER_STORY/" + artifactId + "/review")
                        .with(user("analyst@example.com").roles("BUSINESS_ANALYST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(humanReviewService).review(eq(ReviewArtifactType.USER_STORY), eq(artifactId),
            any(HumanReviewRequest.class), any());
    }

    @Test
    void clientCannotReviewArtifact() throws Exception {
        mockMvc.perform(post("/api/v1/ai-artifacts/SRS_DOCUMENT/" + UUID.randomUUID() + "/review")
                        .with(user("client@example.com").roles("CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new HumanReviewRequest(ReviewAction.ACCEPT, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessAnalystCanRequestReviewHistory() throws Exception {
        UUID artifactId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/ai-artifacts/ACCEPTANCE_CRITERIA/" + artifactId + "/reviews")
                        .with(user("analyst@example.com").roles("BUSINESS_ANALYST")))
                .andExpect(status().isOk());

        verify(humanReviewService).history(eq(ReviewArtifactType.ACCEPTANCE_CRITERIA), eq(artifactId), any());
    }
}