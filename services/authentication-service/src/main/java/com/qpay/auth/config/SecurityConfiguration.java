package com.qpay.auth.config;
import org.springframework.context.annotation.*;import org.springframework.security.config.annotation.web.builders.HttpSecurity;import org.springframework.security.web.SecurityFilterChain;
@Configuration class SecurityConfiguration {@Bean SecurityFilterChain chain(HttpSecurity h)throws Exception{return h.csrf(c->c.disable()).authorizeHttpRequests(a->a.requestMatchers("/api/v1/auth/**","/.well-known/**","/actuator/health/**").permitAll().anyRequest().authenticated()).build();}}
