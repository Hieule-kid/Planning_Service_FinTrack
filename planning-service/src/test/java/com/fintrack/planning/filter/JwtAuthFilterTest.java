package com.fintrack.planning.filter;

import com.fintrack.planning.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthFilter}.
 *
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService    jwtService;
    @Mock private HttpServletRequest  request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain   filterChain;

    @InjectMocks private JwtAuthFilter filter;

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_noAuthorizationHeader_proceedsWithoutSettingAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtService, never()).extractUserId(org.mockito.ArgumentMatchers.anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_authHeaderWithoutBearerPrefix_proceedsWithoutAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtService, never()).extractUserId(org.mockito.ArgumentMatchers.anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_emptyBearerToken_proceedsWithoutAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer ");
        when(jwtService.extractUserId("")).thenThrow(new JwtException("empty token"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_validToken_setsSecurityContextPrincipalToUserId() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtService.extractUserId("valid-token")).thenReturn("user-42");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("user-42");
    }

    @Test
    void doFilter_validToken_doesNotOverrideExistingAuthentication() throws Exception {
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken existing =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "existing-user", null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);

        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtService.extractUserId("valid-token")).thenReturn("new-user");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("existing-user");
    }

    @Test
    void doFilter_jwtException_proceedsUnauthenticatedWithoutThrowing() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer expired-token");
        when(jwtService.extractUserId("expired-token"))
                .thenThrow(new JwtException("Token expired"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_unexpectedException_proceedsUnauthenticatedWithoutThrowing() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        when(jwtService.extractUserId("bad-token"))
                .thenThrow(new RuntimeException("Unexpected error"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
