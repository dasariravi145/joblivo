package com.joblivo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Production-ready Spring Security configuration establishing a modular, stateless REST foundation.
 */
@Configuration
@EnableWebSecurity
@Import({RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
public class SecurityConfig {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * Adaptive BCrypt password encoder for future authentication flows.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Primary security filter chain enforcing stateless REST API security.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF protection is disabled because this is a stateless REST API
            // where requests are not authenticated via browser session cookies.
            .csrf(AbstractHttpConfigurer::disable)
            // No broad CORS policy is introduced at this foundation stage to avoid wildcard origin exposure.
            .cors(AbstractHttpConfigurer::disable)
            // Disable default browser interactive login flows
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            // Configure stateless session management (no server-side HTTP session created or used)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Enable standard secure HTTP response headers (X-Content-Type-Options, Frame-Options, HSTS, Cache-Control)
            .headers(Customizer.withDefaults())
            // Configure custom machine-readable REST error entry points (401 and 403)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            // Narrowly scoped public endpoints; all other endpoints require authentication
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
