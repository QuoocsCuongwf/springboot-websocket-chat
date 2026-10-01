package com.quoocscuongwf.nextchat.room.dto;

import com.quoocscuongwf.nextchat.room.Conversation;
import java.time.Instant;
import java.util.List;

public record RoomDTO(
        Long id,
        String type,
        String name,
        String avatarUrl,
        Long createdById,
        MessageDTO lastMessage,
        long unreadCount,
        List<ParticipantDTO> participants,
        Instant createdAt,
        Instant updatedAt
) {
    public static RoomDTO fromEntity(Conversation c, Long currentUserId) {
        return fromEntity(c, currentUserId, 0L);
    }

    public static RoomDTO fromEntity(Conversation c, Long currentUserId, long unreadCount) {
        if (c == null) return null;

        String displayName = c.getName();
        String displayAvatar = c.getAvatarUrl();

        List<ParticipantDTO> participantDTOs = c.getParticipants() != null
                ? c.getParticipants().stream()
                .filter(p -> p.getLeftAt() == null)
                .map(ParticipantDTO::fromEntity)
                .toList()
                : List.of();

        // If PRIVATE chat, determine name & avatar from the partner
        if ("PRIVATE".equalsIgnoreCase(c.getType()) && c.getParticipants() != null) {
            var otherParticipant = c.getParticipants().stream()
                    .filter(p -> p.getUser() != null && !p.getUser().getId().equals(currentUserId))
                    .findFirst();

            if (otherParticipant.isPresent()) {
                var otherUser = otherParticipant.get().getUser();
                if (displayName == null || displayName.isBlank()) {
                    displayName = otherUser.getFullName() != null && !otherUser.getFullName().isBlank()
                            ? otherUser.getFullName()
                            : otherUser.getUsername();
                }
                if (displayAvatar == null || displayAvatar.isBlank()) {
                    displayAvatar = otherUser.getAvatarUrl();
                }
            }
        }

        return new RoomDTO(
                c.getId(),
                c.getType(),
                displayName,
                displayAvatar,
                c.getCreatedBy() != null ? c.getCreatedBy().getId() : null,
                MessageDTO.fromEntity(c.getLastMessage()),
                unreadCount,
                participantDTOs,
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
