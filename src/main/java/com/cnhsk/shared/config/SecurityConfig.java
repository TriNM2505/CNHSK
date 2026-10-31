package com.cnhsk.shared.config;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.AccessDeniedApiException;
import com.cnhsk.shared.security.AuthenticationApiException;
import com.cnhsk.shared.security.JwtAuthenticationFilter;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SessionInvalidationService;
import com.cnhsk.shared.security.RouteCsrfTokenRequestHandler;
import com.cnhsk.shared.security.RouteRoleGuardFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Bean
    public AuthenticationEntryPoint apiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, exception) -> {
            ErrorCode code = exception instanceof AuthenticationApiException apiException
                    ? apiException.getErrorCode() : ErrorCode.UNAUTHENTICATED;
            String message = exception.getMessage();
            Map<String, Object> extra = exception instanceof AuthenticationApiException apiException
                    ? apiException.getExtra() : Map.of();
            writeError(response, objectMapper, request, HttpStatus.UNAUTHORIZED, code, message, extra);
        };
    }

    @Bean
    public AccessDeniedHandler apiAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, exception) -> {
            ErrorCode code = exception instanceof AccessDeniedApiException apiException
                    ? apiException.getErrorCode()
                    : exception instanceof CsrfException ? ErrorCode.CSRF_TOKEN_INVALID : ErrorCode.FORBIDDEN;
            String message = exception.getMessage();
            Map<String, Object> extra = exception instanceof AccessDeniedApiException apiException
                    ? apiException.getExtra() : Map.of();
            writeError(response, objectMapper, request, HttpStatus.FORBIDDEN, code, message, extra);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            CnhskProperties properties,
            JwtService jwtService,
            SessionInvalidationService invalidationService,
            AuthenticationEntryPoint apiAuthenticationEntryPoint,
            AccessDeniedHandler apiAccessDeniedHandler,
            Environment environment) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = csrfTokenRepository(properties);
        boolean swaggerEnabled = environment.acceptsProfiles(Profiles.of("dev"))
                && !environment.acceptsProfiles(Profiles.of("prod"));
        RequestMatcher importRoutes = request -> {
            String path = request.getServletPath();
            if (path.isEmpty()) {
                path = request.getRequestURI().substring(request.getContextPath().length());
            }
            return "/api/admin/imports".equals(path) || path.startsWith("/api/admin/imports/");
        };
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .csrfTokenRequestHandler(new RouteCsrfTokenRequestHandler(importRoutes))
                        .requireCsrfProtectionMatcher(cookieUnsafeRequestMatcher()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                            .requestMatchers(HttpMethod.POST,
                                    "/api/auth/register", "/api/auth/verify-email", "/api/auth/login",
                                    "/api/auth/refresh", "/api/auth/forgot-password", "/api/auth/reset-password")
                            .permitAll();
                    if (swaggerEnabled) {
                        authorize.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html").permitAll();
                    } else {
                        authorize.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html").denyAll();
                    }
                    authorize.requestMatchers("/api/admin/imports", "/api/admin/imports/**").hasRole("CONTENT_EDITOR");
                    authorize.requestMatchers(HttpMethod.GET, "/api/practice/sessions/active",
                            "/api/practice/sessions/{id}").hasRole("LEARNER");
                    authorize.requestMatchers(HttpMethod.POST, "/api/practice/sessions/{id}/answers").hasRole("LEARNER");
                    authorize.requestMatchers(HttpMethod.POST, "/api/practice/sessions/{id}/finish").hasRole("LEARNER");
                    authorize.requestMatchers(HttpMethod.POST, "/api/practice/sessions/{id}/abandon").hasRole("LEARNER");
                    authorize.requestMatchers(HttpMethod.GET, "/api/topics").hasRole("LEARNER");
                    authorize.requestMatchers(HttpMethod.GET, "/api/topics/{id}").hasRole("LEARNER");
                    authorize.requestMatchers(HttpMethod.GET, "/api/grammar", "/api/grammar/{id}").hasRole("LEARNER");
                    authorize.requestMatchers(HttpMethod.GET, "/api/characters").hasRole("LEARNER");
                    authorize.requestMatchers(HttpMethod.GET, "/api/characters/{id}").hasRole("LEARNER");
                    authorize.requestMatchers(HttpMethod.POST, "/api/characters/recognition-sessions").hasRole("LEARNER");
                    authorize.requestMatchers(HttpMethod.POST, "/api/topics/{id}/study-sessions").hasRole("LEARNER");
                    authorize.requestMatchers(HttpMethod.POST, "/api/topics/{id}/practice-sessions").hasRole("LEARNER");
                    authorize.requestMatchers(HttpMethod.POST, "/api/topics/{id}/tests").hasRole("LEARNER");
                    authorize.requestMatchers(HttpMethod.GET, "/api/pronunciation/stages").hasRole("LEARNER");
                    authorize.requestMatchers(HttpMethod.POST, "/api/pronunciation/stages/{stageIndex}/sessions").hasRole("LEARNER");
                    authorize.requestMatchers("/api/admin/**").hasRole("ADMIN")
                            .requestMatchers("/api/auth/verify-email/resend", "/api/auth/logout",
                                    "/api/auth/logout-all", "/api/auth/me", "/api/me/**")
                            .authenticated()
                            .anyRequest().denyAll();
                })
                .addFilterBefore(new RouteRoleGuardFilter(importRoutes,
                                new JwtAuthenticationFilter(jwtService, invalidationService,
                                        apiAuthenticationEntryPoint, apiAccessDeniedHandler),
                                "CONTENT_EDITOR", apiAuthenticationEntryPoint, apiAccessDeniedHandler),
                        org.springframework.security.web.csrf.CsrfFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, invalidationService,
                                apiAuthenticationEntryPoint, apiAccessDeniedHandler),
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource(CnhskProperties properties) {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        configuration.setAllowCredentials(properties.getCors().isAllowCredentials());
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "X-Client-Type",
                CSRF_HEADER, "X-Request-Id"));
        configuration.setExposedHeaders(java.util.List.of("X-Request-Id"));
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source =
                new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public FilterRegistrationBean<Filter> requestIdFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    private static CookieCsrfTokenRepository csrfTokenRepository(CnhskProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(XSRF_COOKIE);
        repository.setHeaderName(CSRF_HEADER);
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .domain(properties.getCookie().getDomain())
                .secure(properties.getCookie().isSecure())
                .sameSite(properties.getCookie().getSameSite())
                .httpOnly(false));
        return repository;
    }

    private static RequestMatcher cookieUnsafeRequestMatcher() {
        return request -> {
            String method = request.getMethod();
            if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method)
                    || HttpMethod.TRACE.matches(method) || HttpMethod.OPTIONS.matches(method)) {
                return false;
            }
            String path = request.getServletPath();
            if (isCsrfExemptPath(path)) {
                return false;
            }
            jakarta.servlet.http.Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return false;
            }
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if (ACCESS_COOKIE.equals(cookie.getName())) {
                    return true;
                }
            }
            return false;
        };
    }

    private static boolean isCsrfExemptPath(String path) {
        return "/api/auth/login".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/forgot-password".equals(path)
                || "/api/auth/reset-password".equals(path)
                || "/api/auth/verify-email".equals(path)
                || "/api/auth/refresh".equals(path);
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper,
            HttpServletRequest request, HttpStatus status, ErrorCode code, String message,
            Map<String, Object> extra) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error_code", code.name());
        body.put("message", message == null || message.isBlank() ? code.name() : message);
        body.put("request_id", request.getAttribute(RequestIdFilter.MDC_KEY) instanceof String requestId
                ? requestId : RequestIdFilter.currentRequestId());
        if (extra != null) {
            extra.forEach((key, value) -> {
                if (!"error_code".equals(key) && !"message".equals(key) && !"request_id".equals(key)) {
                    body.put(key, value);
                }
            });
        }
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
