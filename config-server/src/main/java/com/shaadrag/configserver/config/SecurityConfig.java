package com.shaadrag.configserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "spring.services.creds")
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private  String identitySvcName;
    private  String gatewaySvcName;
    private  String identitySvcPassword;
    private  String gatewaySvcPassword;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }


    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService users() {
        UserDetails identity = User.withUsername(identitySvcName)
                .password(passwordEncoder().encode(identitySvcPassword))
                .build();

        UserDetails gateway = User.withUsername(gatewaySvcName)
                .password(passwordEncoder().encode(gatewaySvcPassword))
                .build();

        return new InMemoryUserDetailsManager(identity, gateway);
    }

}
