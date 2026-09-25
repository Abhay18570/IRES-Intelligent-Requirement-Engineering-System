package com.ires.requirement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ires.auth.security.CustomUserDetailsService;
import com.ires.auth.security.JwtAuthenticationFilter;
import com.ires.auth.security.JwtService;
import com.ires.common.exception.GlobalExceptionHandler;
import com.ires.common.exception.NotFoundException;
import com.ires.config.SecurityConfig;
import com.ires.requirement.dto.RequirementCreateRequest;
import com.ires.requirement.entity.RequirementPriority;
import com.ires.requirement.entity.RequirementStatus;
import com.ires.requirement.entity.RequirementType;
import com.ires.requirement.service.RequirementService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RequirementController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class RequirementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RequirementService requirementService;

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
    void rejectsUnauthenticatedRequirementRequest() throws Exception {
        mockMvc.perform(get("/api/v1/requirements/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsBlankRequirementTitle() throws Exception {
        RequirementCreateRequest request = new RequirementCreateRequest(
                "", "Details", RequirementType.FUNCTIONAL, RequirementPriority.MEDIUM,
                RequirementStatus.DRAFT, "client", null);

        mockMvc.perform(post("/api/v1/projects/" + UUID.randomUUID() + "/requirements")
                        .with(user("client@example.com").roles("CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForUnknownRequirement() throws Exception {
        when(requirementService.get(any(UUID.class), any()))
                .thenThrow(new NotFoundException("Requirement not found."));

        mockMvc.perform(get("/api/v1/requirements/" + UUID.randomUUID())
                        .with(user("client@example.com").roles("CLIENT")))
                .andExpect(status().isNotFound());
    }
}
