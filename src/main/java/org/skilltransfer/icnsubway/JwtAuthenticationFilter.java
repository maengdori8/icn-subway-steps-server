package org.skilltransfer.icnsubway;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Authorization 헤더가 없으면 익명으로 통과시킨다. 헤더가 있으면 유효한 Bearer 토큰이어야 하며,
 * 아니면 공개 GET이라도 바로 401을 돌려준다. /api/auth/**와 /h2-console/**는 헤더를 보지 않는다.
 * SecurityConfig에서 직접 만든다(빈으로 등록하면 서블릿 필터로 한 번 더 걸린다).
 */
class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER = "Bearer ";

    private final JwtService jwt;
    private final UserRepository users;
    private final JsonMapper json;

    JwtAuthenticationFilter(JwtService jwt, UserRepository users, JsonMapper json) {
        this.jwt = jwt;
        this.users = users;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api/auth/") || path.startsWith("/h2-console");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        AuthUser user = authenticate(header);
        if (user == null) {
            SecurityContextHolder.clearContext();
            ApiError.write(response, json, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "로그인이 만료되었거나 올바르지 않습니다.");
            return;
        }
        var auth = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }

    private AuthUser authenticate(String header) {
        if (!header.regionMatches(true, 0, BEARER, 0, BEARER.length())) return null;
        String token = header.substring(BEARER.length()).trim();
        if (token.isEmpty()) return null;
        try {
            JwtService.Verified v = jwt.verify(token);
            // DB만 초기화하고 키를 남기면 id가 다른 사람에게 다시 배정될 수 있다.
            // 그래서 id뿐 아니라 이름과 '계정이 생긴 뒤에 발급됐는지'까지 맞아야 받는다.
            return users.findById(v.userId())
                    .filter(u -> u.getUsername().equals(v.username()))
                    .filter(u -> !v.issuedAt().isBefore(u.getCreatedAt().truncatedTo(ChronoUnit.SECONDS)))
                    .map(u -> new AuthUser(u.getId(), u.getUsername()))
                    .orElse(null);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
