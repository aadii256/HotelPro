
package com.hotelpro.security;

import com.hotelpro.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            String email = jwtService.extractUsername(token);

            if (email != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                var optionalUser = userRepository.findByEmail(email);

                if (optionalUser.isPresent()) {
                    var appUser = optionalUser.get();

                    UserDetails userDetails = User.withUsername(appUser.email)
                            .password(appUser.password)
                            .authorities("ROLE_" + appUser.role)
                            .build();

                    if (jwtService.isTokenValid(token, userDetails)) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());

                        authentication.setDetails(
                                new WebAuthenticationDetailsSource()
                                        .buildDetails(request));

                        SecurityContextHolder.getContext()
                                .setAuthentication(authentication);
                    }
                }
            }
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            @Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();

    return path.equals("/api/auth/register")
            || path.equals("/api/auth/login")
            || path.equals("/api/health")
            || request.getMethod().equals("OPTIONS");
}
        }

        filterChain.doFilter(request, response);
    }
}
