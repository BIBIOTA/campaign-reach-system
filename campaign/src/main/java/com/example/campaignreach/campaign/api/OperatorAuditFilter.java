package com.example.campaignreach.campaign.api;

import com.example.campaignreach.campaign.domain.CurrentOperator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Attributes the authenticated operator id to the audit columns for the duration of a request (task
 * 4.1, §10).
 *
 * <p>Runs after authentication: when the current principal is an {@link OperatorUserDetails} it
 * {@link CurrentOperator#set(java.util.UUID) sets} the operator id so Spring Data JPA auditing
 * stamps {@code created_by}/{@code updated_by}. It honors the {@link CurrentOperator} leak-safety
 * contract by always {@link CurrentOperator#clear() clearing} in a {@code finally} block so a pooled
 * thread never carries a prior operator id into the next request.
 */
public class OperatorAuditFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OperatorUserDetails operator) {
            CurrentOperator.set(operator.id());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            CurrentOperator.clear();
        }
    }
}
