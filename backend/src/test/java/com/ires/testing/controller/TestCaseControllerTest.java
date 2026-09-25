package com.ires.testing.controller;

import com.ires.auth.security.CustomUserDetailsService;
import com.ires.auth.security.JwtAuthenticationFilter;
import com.ires.auth.security.JwtService;
import com.ires.common.exception.GlobalExceptionHandler;
import com.ires.config.SecurityConfig;
import com.ires.project.service.ProjectService;
import com.ires.requirement.service.RequirementService;
import com.ires.story.repository.UserStoryRepository;
import com.ires.testing.service.TestCaseService;
import com.ires.user.repository.UserRepository;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TestCaseController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TestCaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TestCaseService testCaseService;
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
    @MockBean
    private ProjectService projectService;
    @MockBean
    private RequirementService requirementService;
    @MockBean
    private UserStoryRepository userStoryRepository;
    @MockBean
    private UserRepository userRepository;

    @BeforeEach
    void letRequestsPassThroughMockedJwtFilter() throws Exception {
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void testCaseListingRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID() + "/test-cases"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAuthenticatedTesterToListTestCases() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID() + "/test-cases")
                        .with(user("tester@example.com").roles("TESTER")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsClientTestExecution() throws Exception {
        mockMvc.perform(post("/api/v1/test-cases/" + UUID.randomUUID() + "/execute")
                        .with(user("client@example.com").roles("CLIENT"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"executionStatus\":\"PASS\",\"actualResult\":\"ok\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsTesterTestExecution() throws Exception {
        mockMvc.perform(post("/api/v1/test-cases/" + UUID.randomUUID() + "/execute")
                        .with(user("tester@example.com").roles("TESTER"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"executionStatus\":\"PASS\",\"actualResult\":\"ok\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void allowsAdminTestExecution() throws Exception {
        mockMvc.perform(post("/api/v1/test-cases/" + UUID.randomUUID() + "/execute")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"executionStatus\":\"PASS\",\"actualResult\":\"ok\"}"))
                .andExpect(status().isCreated());
    }
}
