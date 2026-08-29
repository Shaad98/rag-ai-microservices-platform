package com.shaadrag.identity.config;

import com.shaadrag.identity.security.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.core.userdetails.UserDetailsService;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
// import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;

        @Bean
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        AuthenticationProvider authenticationProvider)
                        throws Exception {

                http
                                .csrf(csrf -> csrf.disable())

                                // Gateway will take care of the endpoint /auth/refresh no need to create
                                // ambiguity

                                // .csrf(csrf -> csrf
                                // // .ignoringRequestMatchers("/user/**")
                                // .csrfTokenRepository(
                                // CookieCsrfTokenRepository.withHttpOnlyFalse())
                                // .requireCsrfProtectionMatcher(request -> request.getMethod().equals("POST")
                                // && request.getServletPath().equals("/auth/refresh")))

                                .sessionManagement(session -> session.sessionCreationPolicy(
                                                SessionCreationPolicy.STATELESS))

                                .authenticationProvider(authenticationProvider)

                                .authorizeHttpRequests(auth -> auth

                                                .requestMatchers(
                                                                "/auth/register",
                                                                "/auth/login",
                                                                "/auth/refresh",
                                                                "/auth/logout")
                                                .permitAll()

                                                .requestMatchers(
                                                                "/email-verification/verify")
                                                .permitAll()

                                                .requestMatchers(
                                                                "/actuator/health")
                                                .permitAll()

                                                .requestMatchers("/admin/**").hasRole("ADMIN")

                                                .anyRequest().authenticated())

                                // ⭐ JWT filter runs before Spring's
                                // UsernamePasswordAuthenticationFilter
                                .addFilterBefore(
                                                jwtAuthenticationFilter,
                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public AuthenticationProvider authenticationProvider(
                        UserDetailsService userDetailsService,
                        PasswordEncoder passwordEncoder) {

                DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);

                // provider.setUserDetailsService();
                provider.setPasswordEncoder(passwordEncoder);

                return provider;
        }

        @Bean
        public AuthenticationManager authenticationManager(
                        AuthenticationConfiguration configuration)
                        throws Exception {

                return configuration.getAuthenticationManager();
        }
}