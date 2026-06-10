package com.example.campaignreach.campaign.api;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * Secures the internal (non-public) campaign REST with HTTP Basic and the single back-office {@code
 * OPERATOR} role (task 4.1, §10).
 *
 * <p>Unauthenticated calls to {@code /internal/campaigns/**} get 401; authenticated callers without
 * {@code ROLE_OPERATOR} get 403. Operator accounts (username/password/deterministic UUID) are loaded
 * from {@link OperatorProperties}, i.e. from configuration/env — never hardcoded secrets.
 *
 * <p>These endpoints are stateless JSON APIs, so CSRF is disabled and no HTTP session is created:
 * each request carries its own Basic credentials, so there is no browser-form / session-cookie
 * attack surface for CSRF to protect. The {@link OperatorAuditFilter} runs right after authentication
 * to attribute audit columns to the authenticated operator.
 */
@Configuration
@EnableConfigurationProperties(OperatorProperties.class)
public class CampaignSecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * In-memory operator store keyed by username. The custom {@link OperatorUserDetails} (which
     * carries the deterministic operator id) is returned verbatim — unlike {@code
     * InMemoryUserDetailsManager}, which would copy it into a plain {@code User} and drop the id.
     */
    @Bean
    public UserDetailsService operatorUserDetailsService(OperatorProperties properties, PasswordEncoder encoder) {
        Map<String, OperatorUserDetails> store = new HashMap<>();
        for (OperatorProperties.Operator operator : properties.operators()) {
            store.put(
                    operator.username(),
                    new OperatorUserDetails(operator.username(), encoder.encode(operator.password()), operator.id()));
        }
        return username -> {
            OperatorUserDetails found = store.get(username);
            if (found == null) {
                throw new UsernameNotFoundException("unknown operator: " + username);
            }
            return found;
        };
    }

    @Bean
    public SecurityFilterChain campaignSecurityFilterChain(
            HttpSecurity http, UserDetailsService userDetailsService, PasswordEncoder passwordEncoder)
            throws Exception {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        return http.securityMatcher("/internal/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("OPERATOR"))
                .authenticationProvider(provider)
                .httpBasic(basic -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterAfter(new OperatorAuditFilter(), BasicAuthenticationFilter.class)
                .build();
    }
}
