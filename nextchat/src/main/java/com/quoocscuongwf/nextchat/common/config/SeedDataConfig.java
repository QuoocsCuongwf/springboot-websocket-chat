package com.quoocscuongwf.nextchat.config;

import com.quoocscuongwf.nextchat.user.User;
import com.quoocscuongwf.nextchat.user.UserRepository;
import com.quoocscuongwf.nextchat.user.Role;
import com.quoocscuongwf.nextchat.user.RoleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev")
public class SeedDataConfig {
    @Bean
    CommandLineRunner seedUsers(
            UserRepository users,
            RoleRepository roles,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.password:ChangeMe123!}") String seedPassword) {
        return args -> {
            Role userRole = roles.findByName("ROLE_USER")
                    .orElseGet(() -> roles.save(new Role("ROLE_USER")));
            roles.findByName("ROLE_ADMIN")
                    .orElseGet(() -> roles.save(new Role("ROLE_ADMIN")));
            createUserIfMissing(users, passwordEncoder, seedPassword,
                    userRole, "alice", "alice@example.com", "Alice");
            createUserIfMissing(users, passwordEncoder, seedPassword,
                    userRole, "bob", "bob@example.com", "Bob");
        };
    }

    private void createUserIfMissing(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            String seedPassword,
            Role userRole,
            String username,
            String email,
            String fullName) {
        if (!users.existsByUsername(username) && !users.existsByEmail(email)) {
            User user = new User(username, email, passwordEncoder.encode(seedPassword), fullName);
            user.addRole(userRole);
            users.save(user);
        }
    }
}
