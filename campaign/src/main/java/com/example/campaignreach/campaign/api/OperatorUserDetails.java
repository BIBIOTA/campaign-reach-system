package com.example.campaignreach.campaign.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated back-office operator principal (task 4.1).
 *
 * <p>Extends the standard {@link UserDetails} contract with the deterministic operator {@link #id()}
 * so the {@link OperatorAuditFilter} can attribute audit columns to the authenticated identity. All
 * operators hold the single {@code ROLE_OPERATOR} authority.
 */
public final class OperatorUserDetails implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final String username;
    private final String password;
    private final UUID id;

    /** Builds an operator principal from its configured username, password and deterministic id. */
    public OperatorUserDetails(String username, String password, UUID id) {
        this.username = username;
        this.password = password;
        this.id = id;
    }

    /** The deterministic operator UUID attributed to {@code created_by}/{@code updated_by}. */
    public UUID id() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
