package com.paytrix.cipherbank.infrastructure.security;

import com.paytrix.cipherbank.application.service.IpWhitelistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class IpWhitelistFilter extends OncePerRequestFilter {

    private final IpWhitelistService ipWhitelistService;

    public IpWhitelistFilter(IpWhitelistService ipWhitelistService) {
        this.ipWhitelistService = ipWhitelistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = request.getRemoteAddr();
        Set<String> whitelistedIps = ipWhitelistService.getActiveIps();

        if (!whitelistedIps.contains(clientIp)) {

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
//            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.getWriter().write("Access denied: IP not whitelisted");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
