package com.ires.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ires.common.exception.ForbiddenException;
import com.ires.common.exception.GlobalExceptionHandler;
import com.ires.config.SecurityConfig;
import com.ires.project.dto.ProjectCreateRequest;
import com.ires.project.dto.ProjectResponse;
import com.ires.project.dto.ProjectUpdateRequest;
import com.ires.project.entity.ProjectStatus;
import com.ires.project.service.ProjectService;
import com.ires.auth.security.CustomUserDetailsService;
import com.ires.auth.security.JwtAuthenticationFilter;
import com.ires.auth.security.JwtService;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private final ProjectResponse projectResponse = new ProjectResponse(
            UUID.randomUUID(), "Checkout", "Revamp", ProjectStatus.ACTIVE, null, null,
            new com.ires.project.dto.UserSummary(UUID.randomUUID(), "Ada Lovelace", "ada@example.com"), 0, null, null);

    @BeforeEach
    void letRequestsPassThroughMockedJwtFilter() throws Exception {
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void rejectsUnauthenticatedProjectRequest() throws Exception {
        mockMvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    void createsProjectForAuthenticatedClient() throws Exception {
        when(projectService.create(any(ProjectCreateRequest.class), any())).thenReturn(projectResponse);

        mockMvc.perform(post("/api/v1/projects")
                        .with(user("ada@example.com").roles("CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProjectCreateRequest("Checkout", "Revamp", ProjectStatus.ACTIVE, null, null))))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsInvalidDateRange() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .with(user("ada@example.com").roles("CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProjectCreateRequest("Checkout", "Revamp", ProjectStatus.ACTIVE,
                                        java.time.LocalDate.of(2026, 2, 1), java.time.LocalDate.of(2026, 1, 1)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsForbiddenWhenClientCannotUpdateAnotherClientsProject() throws Exception {
        when(projectService.update(any(), any(ProjectUpdateRequest.class), any()))
                .thenThrow(new ForbiddenException("Only the project client or an admin can manage this project."));

        mockMvc.perform(put("/api/v1/projects/" + UUID.randomUUID())
                        .with(user("ada@example.com").roles("CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProjectUpdateRequest("Changed", "Revamp", ProjectStatus.ACTIVE, null, null))))
                .andExpect(status().isForbidden());
    }
}
