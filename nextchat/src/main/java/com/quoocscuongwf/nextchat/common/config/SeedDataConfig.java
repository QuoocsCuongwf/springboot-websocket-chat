package com.quoocscuongwf.nextchat.config;

import com.quoocscuongwf.nextchat.user.User;
import com.quoocscuongwf.nextchat.user.UserRepository;
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
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.password:ChangeMe123!}") String seedPassword) {
        return args -> {
            createUserIfMissing(users, passwordEncoder, seedPassword,
                    "alice", "alice@example.com", "Alice");
            createUserIfMissing(users, passwordEncoder, seedPassword,
                    "bob", "bob@example.com", "Bob");
        };
    }

    private void createUserIfMissing(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            String seedPassword,
            String username,
            String email,
            String fullName) {
        if (!users.existsByUsername(username) && !users.existsByEmail(email)) {
            users.save(new User(username, email, passwordEncoder.encode(seedPassword), fullName));
        }
    }
}
