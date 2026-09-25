package com.ires.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ires.auth.security.CustomUserDetailsService;
import com.ires.auth.security.JwtAuthenticationFilter;
import com.ires.auth.security.JwtService;
import com.ires.common.exception.ConflictException;
import com.ires.common.exception.GlobalExceptionHandler;
import com.ires.config.SecurityConfig;
import com.ires.user.dto.LoginRequest;
import com.ires.user.dto.RegisterRequest;
import com.ires.user.dto.UserResponse;
import com.ires.user.entity.RoleName;
import com.ires.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private final UserResponse userResponse = new UserResponse(
            null, "Ada", "Lovelace", "ada@example.com", null, "CLIENT",
            Set.of("CLIENT"), true, null, null);

    @BeforeEach
    void letRequestsPassThroughMockedJwtFilter() throws Exception {
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void registersSuccessfully() throws Exception {
        when(userService.register(any(RegisterRequest.class))).thenReturn(userResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Ada", "Lovelace", "ada@example.com", "Password1", RoleName.CLIENT))))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsDuplicateRegistration() throws Exception {
        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new ConflictException("An account with this email already exists."));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Ada", "Lovelace", "ada@example.com", "Password1", RoleName.CLIENT))))
                .andExpect(status().isConflict());
    }

    @Test
    void logsInSuccessfully() throws Exception {
        UserDetails principal = User.withUsername("ada@example.com")
                .password("hashed-password")
                .authorities("ROLE_CLIENT")
                .build();
        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(userService.findByLogin("ada@example.com")).thenReturn(java.util.Optional.of(userResponse));
        when(jwtService.generateToken(principal)).thenReturn("jwt-token");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("ada@example.com", "Password1"))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidPassword() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("invalid"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("ada@example.com", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectsCurrentUserEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsCurrentUserEndpointWithValidAuthentication() throws Exception {
        when(userService.findByLogin("ada@example.com")).thenReturn(java.util.Optional.of(userResponse));

        mockMvc.perform(get("/api/v1/auth/me")
                        .with(user("ada@example.com").roles("CLIENT")))
                .andExpect(status().isOk());
    }

    @Test
    void deniesClientAccessToAdminResources() throws Exception {
        mockMvc.perform(get("/api/v1/admin/probe")
                        .with(user("client@example.com").roles("CLIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void permitsAdminThroughAdminAuthorizationRule() throws Exception {
        mockMvc.perform(get("/api/v1/admin/probe")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }
}
