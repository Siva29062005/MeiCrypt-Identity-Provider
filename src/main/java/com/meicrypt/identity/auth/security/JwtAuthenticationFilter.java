package com.meicrypt.identity.auth.security;

import com.meicrypt.identity.auth.service.JwtService;
import com.meicrypt.identity.auth.service.SessionCacheService;
import com.meicrypt.identity.rbac.service.UserAuthorityService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Parses the Bearer JWT on every request, validates it, ensures the session is still
 * live in Redis (Module 3.4), and installs an {@link AuthenticatedUser} principal.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SessionCacheService sessionCacheService;
    private final UserAuthorityService userAuthorityService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   SessionCacheService sessionCacheService,
                                   UserAuthorityService userAuthorityService) {
        this.jwtService = jwtService;
        this.sessionCacheService = sessionCacheService;
        this.userAuthorityService = userAuthorityService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length()).trim();
        try {
            Claims claims = jwtService.parse(token);
            String jti = claims.getId();
            if (jti != null && sessionCacheService.isAccessTokenBlacklisted(jti)) {
                filterChain.doFilter(request, response);
                return;
            }
            UUID sessionId = UUID.fromString(claims.get(JwtService.CLAIM_SESSION_ID, String.class));
            if (!sessionCacheService.isSessionActive(sessionId)) {
                filterChain.doFilter(request, response);
                return;
            }

            UUID userId = UUID.fromString(claims.getSubject());
            UUID organizationId = UUID.fromString(claims.get(JwtService.CLAIM_ORGANIZATION_ID, String.class));
            AuthenticatedUser principal = new AuthenticatedUser(
                    userId,
                    organizationId,
                    sessionId,
                    claims.get(JwtService.CLAIM_EMAIL, String.class),
                    jti
            );

            Set<GrantedAuthority> authorities = userAuthorityService.loadAuthorities(userId, organizationId);
            var authentication = new UsernamePasswordAuthenticationToken(principal, token, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ex) {
            logger.debug("Rejected access token: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
