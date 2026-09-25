package com.ires.requirement.criteria.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ires.auth.security.CustomUserDetailsService;
import com.ires.auth.security.JwtAuthenticationFilter;
import com.ires.auth.security.JwtService;
import com.ires.common.exception.GlobalExceptionHandler;
import com.ires.config.SecurityConfig;
import com.ires.requirement.criteria.dto.AcceptanceCriteriaCreateRequest;
import com.ires.requirement.criteria.service.AcceptanceCriteriaService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AcceptanceCriteriaController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AcceptanceCriteriaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AcceptanceCriteriaService criteriaService;

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
    void requiresAuthenticationForListingCriteria() throws Exception {
        mockMvc.perform(get("/api/v1/requirements/" + UUID.randomUUID() + "/acceptance-criteria"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsBlankCriteriaTitle() throws Exception {
        AcceptanceCriteriaCreateRequest request = new AcceptanceCriteriaCreateRequest(
                null, "  ", "Description", null, null);

        mockMvc.perform(post("/api/v1/requirements/" + UUID.randomUUID() + "/acceptance-criteria")
                        .with(user("owner@example.com").roles("CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
