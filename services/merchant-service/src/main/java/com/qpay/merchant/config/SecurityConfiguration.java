package com.qpay.merchant.config;
import org.springframework.context.annotation.*;import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;import org.springframework.security.config.annotation.web.builders.HttpSecurity;import org.springframework.security.oauth2.server.resource.authentication.*;import org.springframework.security.web.SecurityFilterChain;
@Configuration @EnableMethodSecurity class SecurityConfiguration {
 @Bean JwtAuthenticationConverter jwtAuthenticationConverter(){var roles=new JwtGrantedAuthoritiesConverter();roles.setAuthoritiesClaimName("roles");roles.setAuthorityPrefix("ROLE_");var converter=new JwtAuthenticationConverter();converter.setJwtGrantedAuthoritiesConverter(roles);return converter;}
 @Bean SecurityFilterChain chain(HttpSecurity h,JwtAuthenticationConverter converter)throws Exception{return h.csrf(c->c.disable()).authorizeHttpRequests(a->a.requestMatchers("/actuator/health/**").permitAll().anyRequest().authenticated()).oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter))).build();}
}
