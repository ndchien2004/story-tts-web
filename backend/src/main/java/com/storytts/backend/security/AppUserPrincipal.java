package com.storytts.backend.security;

import com.storytts.backend.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Người dùng đang đăng nhập, bọc quanh entity {@link User}.
 * Quyền được phát ra dưới dạng authority để dùng trực tiếp trong Spring Security:
 * ROLE_ADMIN / ROLE_MEMBER và authority phụ "VIP".
 */
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String email;
    private final String password;
    private final boolean vip;
    private final boolean admin;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    public AppUserPrincipal(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.vip = user.isVip();
        this.admin = user.isAdmin();
        this.enabled = user.isEnabled();

        List<GrantedAuthority> list = new ArrayList<>();
        list.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        if (user.isVip()) {
            list.add(new SimpleGrantedAuthority("VIP"));
        }
        this.authorities = List.copyOf(list);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public boolean isVip() {
        return vip;
    }

    public boolean isAdmin() {
        return admin;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
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
        return enabled;
    }
}
