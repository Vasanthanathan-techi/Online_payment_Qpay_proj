package com.qpay.wallet.config;
import jakarta.servlet.*;import jakarta.servlet.http.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;import org.springframework.security.core.authority.SimpleGrantedAuthority;import org.springframework.security.core.context.SecurityContextHolder;import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.List;
@Component class ServiceTokenFilter extends OncePerRequestFilter {
 private final byte[] expected;ServiceTokenFilter(@Value("${qpay.service-token:local-service-token}") String token){expected=token.getBytes(StandardCharsets.UTF_8);}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{String token=request.getHeader("X-QPay-Service-Token");if(token!=null&&MessageDigest.isEqual(expected,token.getBytes(StandardCharsets.UTF_8))){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("internal-service",null,List.of(new SimpleGrantedAuthority("SCOPE_wallet.internal"))));}chain.doFilter(request,response);}
}
