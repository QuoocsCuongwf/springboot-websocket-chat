package com.quoocscuongwf.nextchat.security;

import com.quoocscuongwf.nextchat.user.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public DatabaseUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        var user = users.findByUsername(username)
                .filter(found -> found.isActive() && !found.isDeleted())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities("ROLE_USER")
                .build();
    }
}
