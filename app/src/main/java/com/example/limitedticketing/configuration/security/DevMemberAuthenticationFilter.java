package com.example.limitedticketing.configuration.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile({"local", "test"})
public class DevMemberAuthenticationFilter extends OncePerRequestFilter {

    private static final String MEMBER_ID_HEADER = "X-Member-Id";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String memberIdHeader = request.getHeader(MEMBER_ID_HEADER);

        if (memberIdHeader != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Long memberId = Long.valueOf(memberIdHeader);
                if (memberId <= 0) {
                    throw new NumberFormatException();
                }

                MemberPrincipal principal = new MemberPrincipal(memberId);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (NumberFormatException exception) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid X-Member-Id header");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
