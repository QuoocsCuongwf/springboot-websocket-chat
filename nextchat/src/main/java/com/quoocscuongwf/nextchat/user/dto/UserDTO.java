package com.quoocscuongwf.nextchat.user.dto;

import com.quoocscuongwf.nextchat.user.User;
import java.time.Instant;

public record UserDTO(
        Long id,
        String username,
        String email,
        String fullName,
        String avatarUrl,
        String bio,
        String status,
        Instant lastSeenAt,
        Instant createdAt
) {
    public static UserDTO fromEntity(User user) {
        if (user == null) return null;
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getStatus(),
                user.getLastSeenAt(),
                user.getCreatedAt()
        );
    }
}
