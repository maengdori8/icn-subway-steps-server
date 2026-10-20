package org.skilltransfer.icnsubway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/** 공개 GET과 가입·로그인만 열고 나머지 /api/**는 인증을 요구한다. 세션·쿠키를 쓰지 않는다. */
@Configuration
public class SecurityConfig {

    /**
     * dev 프로필에서만 H2 콘솔을 따로 연다(프레임 sameOrigin, CSRF 제외).
     * 빈 이름을 h2Console로 하면 Spring Boot의 H2 콘솔 서블릿 빈과 겹쳐 시작하지 못한다.
     */
    @Bean
    @Order(1)
    @Profile("dev")
    SecurityFilterChain h2ConsoleSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher("/h2-console/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(h -> h.frameOptions(f -> f.sameOrigin()));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain api(HttpSecurity http, JwtService jwt, UserRepository users, JsonMapper json) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/", "/api/health", "/api/routes", "/api/routes/*/steps",
                                "/api/tips", "/api/tips/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                ApiError.write(res, json, 401, "UNAUTHORIZED", "로그인이 필요합니다."))
                        .accessDeniedHandler((req, res, ex) ->
                                ApiError.write(res, json, 403, "FORBIDDEN", "권한이 없습니다.")))
                .addFilterBefore(new JwtAuthenticationFilter(jwt, users, json), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 로그인은 AuthService가 직접 처리한다. 이 빈은 Spring Boot의 기본 사용자(임시 비밀번호 로그)를 끈다. */
    @Bean
    UserDetailsService noDefaultUser() {
        return username -> { throw new UsernameNotFoundException(username); };
    }
}
