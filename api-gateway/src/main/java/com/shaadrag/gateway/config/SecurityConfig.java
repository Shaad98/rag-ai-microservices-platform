package com.shaadrag.gateway.config;

import com.shaadrag.gateway.security.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// import org.springframework.security.authentication.AuthenticationManager;
// import org.springframework.security.authentication.AuthenticationProvider;
// import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

// import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;

// import org.springframework.security.core.userdetails.UserDetailsService;

// import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
// import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
// import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                // .csrf(csrf -> csrf
                // .csrfTokenRepository(
                // CookieCsrfTokenRepository.withHttpOnlyFalse()
                // )
                // .requireCsrfProtectionMatcher(request ->
                // request.getMethod().equals("POST")
                // && request.getServletPath().equals("/auth/refresh")
                // )
                // )

                // .csrf(csrf -> csrf
                //         .csrfTokenRepository(
                //                 CookieCsrfTokenRepository.withHttpOnlyFalse())
                //         .requireCsrfProtectionMatcher(request -> request.getMethod().equals("POST")
                //                 && (request.getServletPath().equals("/auth/refresh")
                //                         || request.getServletPath().equals("/auth/logout"))))


                .csrf(customizer->customizer.disable())

                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/register",
                                "/auth/login",
                                "/auth/logout",
                                "/auth/refresh",
                                "/actuator/health")
                        .permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        .anyRequest().authenticated())

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}