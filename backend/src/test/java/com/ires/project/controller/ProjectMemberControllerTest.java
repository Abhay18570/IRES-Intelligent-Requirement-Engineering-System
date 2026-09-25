package com.ires.project.controller;

import com.ires.auth.security.CustomUserDetailsService;
import com.ires.auth.security.JwtAuthenticationFilter;
import com.ires.auth.security.JwtService;
import com.ires.common.exception.GlobalExceptionHandler;
import com.ires.common.exception.NotFoundException;
import com.ires.config.SecurityConfig;
import com.ires.project.service.ProjectMemberService;
import com.ires.project.service.ProjectService;
import com.ires.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectMemberController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ProjectMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectMemberService projectMemberService;

    @MockBean
    private ProjectService projectService;

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
    void returnsNotFoundForMissingProject() throws Exception {
        when(projectMemberService.list(any(UUID.class), any()))
                .thenThrow(new NotFoundException("Project not found."));

        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID() + "/members")
                        .with(user("ada@example.com").roles("CLIENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnauthenticatedMemberRequest() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID() + "/members"))
                .andExpect(status().isUnauthorized());
    }
}
