package com.ravan.SpringBootLab.security;

import com.ravan.SpringBootLab.model.User;
import com.ravan.SpringBootLab.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        filter = new JwtAuthenticationFilter(
                jwtService,
                userRepository
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldContinueWhenAuthorizationHeaderIsMissing()
            throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn(null);

        filter.doFilterInternal(
                request,
                response,
                filterChain
        );

        verify(filterChain)
                .doFilter(request, response);

        verifyNoInteractions(
                jwtService,
                userRepository
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );
    }

    @Test
    void shouldContinueWhenAuthorizationSchemeIsNotBearer()
            throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn("Basic abc123");

        filter.doFilterInternal(
                request,
                response,
                filterChain
        );

        verify(filterChain)
                .doFilter(request, response);

        verifyNoInteractions(
                jwtService,
                userRepository
        );
    }

    @Test
    void shouldNotAuthenticateWhenExtractedEmailIsNull()
            throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer token");

        when(jwtService.extractEmail("token"))
                .thenReturn(null);

        filter.doFilterInternal(
                request,
                response,
                filterChain
        );

        verify(jwtService)
                .extractEmail("token");

        verifyNoInteractions(userRepository);

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(filterChain)
                .doFilter(request, response);
    }

    @Test
    void shouldPreserveExistingAuthentication()
            throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer token");

        when(jwtService.extractEmail("token"))
                .thenReturn("user@example.com");

        UsernamePasswordAuthenticationToken existing =
                new UsernamePasswordAuthenticationToken(
                        "existing-user",
                        null
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(existing);

        filter.doFilterInternal(
                request,
                response,
                filterChain
        );

        verify(jwtService)
                .extractEmail("token");

        verifyNoInteractions(userRepository);

        assertSame(
                existing,
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(filterChain)
                .doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticateWhenUserDoesNotExist()
            throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer token");

        when(jwtService.extractEmail("token"))
                .thenReturn("missing@example.com");

        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        filter.doFilterInternal(
                request,
                response,
                filterChain
        );

        verify(userRepository)
                .findByEmail("missing@example.com");

        verify(jwtService, never())
                .isTokenValid(
                        anyString(),
                        any(User.class)
                );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(filterChain)
                .doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticateWhenTokenIsInvalid()
            throws Exception {

        User user = org.mockito.Mockito.mock(User.class);

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer token");

        when(jwtService.extractEmail("token"))
                .thenReturn("user@example.com");

        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(user));

        when(jwtService.isTokenValid("token", user))
                .thenReturn(false);

        filter.doFilterInternal(
                request,
                response,
                filterChain
        );

        verify(jwtService)
                .isTokenValid("token", user);

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(filterChain)
                .doFilter(request, response);
    }

    @Test
    void shouldClearSecurityContextWhenJwtProcessingFails()
            throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer malformed-token");

        when(jwtService.extractEmail("malformed-token"))
                .thenThrow(
                        new IllegalArgumentException("invalid token")
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "old-user",
                                null
                        )
                );

        filter.doFilterInternal(
                request,
                response,
                filterChain
        );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );

        verify(filterChain)
                .doFilter(request, response);
    }
}
