package com.ires.requirement.attachment.controller;

import com.ires.auth.security.CustomUserDetailsService;
import com.ires.auth.security.JwtAuthenticationFilter;
import com.ires.auth.security.JwtService;
import com.ires.common.exception.GlobalExceptionHandler;
import com.ires.config.SecurityConfig;
import com.ires.requirement.attachment.dto.AttachmentResponse;
import com.ires.requirement.attachment.service.RequirementAttachmentService;
import com.ires.requirement.service.RequirementService;
import com.ires.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RequirementAttachmentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class RequirementAttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RequirementAttachmentService attachmentService;

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
    void rejectsUnauthenticatedUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "requirements.pdf", MediaType.APPLICATION_PDF_VALUE, "requirements".getBytes());

        mockMvc.perform(multipart("/api/v1/requirements/" + UUID.randomUUID() + "/attachments").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsAuthenticatedMultipartUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "requirements.pdf", MediaType.APPLICATION_PDF_VALUE, "requirements".getBytes());
        when(attachmentService.upload(any(), any(), any())).thenReturn(null);

        mockMvc.perform(multipart("/api/v1/requirements/" + UUID.randomUUID() + "/attachments")
                        .file(file)
                        .with(user("client@example.com").roles("CLIENT")))
                .andExpect(status().isCreated());
    }

    @Test
    void listsAttachmentsForAuthenticatedUser() throws Exception {
        when(attachmentService.list(any(), any(), any())).thenReturn(new PageImpl<>(List.<AttachmentResponse>of()));

        mockMvc.perform(get("/api/v1/requirements/" + UUID.randomUUID() + "/attachments")
                        .with(user("client@example.com").roles("CLIENT")))
                .andExpect(status().isOk());
    }
}
