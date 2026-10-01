package com.quoocscuongwf.nextchat.user.dto;

import com.quoocscuongwf.nextchat.user.Role;
import com.quoocscuongwf.nextchat.user.User;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

public record UserProfileDTO(
        Long id,
        String username,
        String email,
        String fullName,
        String avatarUrl,
        String bio,
        String phoneNumber,
        String status,
        Instant lastSeenAt,
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserProfileDTO fromEntity(User user) {
        if (user == null) return null;
        Set<String> roleNames = user.getRoles() != null
                ? user.getRoles().stream().map(Role::getName).collect(Collectors.toSet())
                : Set.of();
        return new UserProfileDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getPhoneNumber(),
                user.getStatus(),
                user.getLastSeenAt(),
                roleNames,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
