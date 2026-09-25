package com.shaadrag.document.config;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;


import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.shaadrag.document.security.DocumentAccessDeniedHandler;
import com.shaadrag.document.security.JwtAuthenticationEntryPoint;
import com.shaadrag.document.security.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;

        private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

        private final DocumentAccessDeniedHandler documentAccessDeniedHandler;

        @Bean
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        AuthenticationProvider authenticationProvider) throws Exception {

                http

                                .csrf(csrf -> csrf.disable())

                                .sessionManagement(session -> session.sessionCreationPolicy(
                                                SessionCreationPolicy.STATELESS))

                                .authenticationProvider(
                                                authenticationProvider)

                                .exceptionHandling(exception -> exception
                                                .authenticationEntryPoint(
                                                                jwtAuthenticationEntryPoint)
                                                .accessDeniedHandler(
                                                                documentAccessDeniedHandler))

                                .authorizeHttpRequests(auth -> auth
                                                .anyRequest().authenticated())

                                .addFilterBefore(
                                                jwtAuthenticationFilter,
                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

}