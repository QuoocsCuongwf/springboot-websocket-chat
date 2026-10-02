package com.quoocscuongwf.nextchat.room.dto;

import com.quoocscuongwf.nextchat.room.ConversationParticipant;
import java.time.Instant;

public record ParticipantDTO(
        Long id,
        Long userId,
        String username,
        String fullName,
        String avatarUrl,
        String role,
        String nickname,
        Instant joinedAt
) {
    public static ParticipantDTO fromEntity(ConversationParticipant cp) {
        if (cp == null) return null;
        var u = cp.getUser();
        return new ParticipantDTO(
                cp.getId(),
                u != null ? u.getId() : null,
                u != null ? u.getUsername() : null,
                u != null ? u.getFullName() : null,
                u != null ? u.getAvatarUrl() : null,
                cp.getRole(),
                cp.getNickname(),
                cp.getJoinedAt()
        );
    }
}
