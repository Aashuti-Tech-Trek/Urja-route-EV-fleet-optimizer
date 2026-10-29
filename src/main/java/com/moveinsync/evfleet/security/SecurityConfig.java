package com.moveinsync.evfleet.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public / documentation endpoints
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // GET operations on fleet and plans: VIEWER, OPERATOR, ADMIN
                .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole("VIEWER", "OPERATOR", "ADMIN")

                // Plan Acceptance & Replanning: OPERATOR, ADMIN
                .requestMatchers(HttpMethod.POST, "/api/v1/plans/*/accept").hasAnyRole("OPERATOR", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/plans/propose").hasAnyRole("OPERATOR", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/replanning/**").hasAnyRole("OPERATOR", "ADMIN")

                // Vehicle, Hub, and Trip registration: OPERATOR, ADMIN
                .requestMatchers(HttpMethod.POST, "/api/v1/vehicles/**", "/api/v1/trips/**").hasAnyRole("OPERATOR", "ADMIN")

                // All other API requests must be authenticated
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    response.getWriter().write("""
                        {"type":"https://urjaroute.moveinsync.com/errors/unauthorized","title":"Unauthorized","status":401,"detail":"Missing or invalid API key credentials"}
                        """);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    response.getWriter().write("""
                        {"type":"https://urjaroute.moveinsync.com/errors/forbidden","title":"Forbidden","status":403,"detail":"Access denied: insufficient role permissions"}
                        """);
                })
            )
            .addFilterBefore(new ApiKeyAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
