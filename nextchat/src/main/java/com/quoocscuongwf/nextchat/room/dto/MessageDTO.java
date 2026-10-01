package com.quoocscuongwf.nextchat.room.dto;

import com.quoocscuongwf.nextchat.room.Message;
import java.time.Instant;

public record MessageDTO(
        Long id,
        Long roomId,
        Long senderId,
        String senderUsername,
        String senderFullName,
        String senderAvatarUrl,
        String messageType,
        String content,
        Long replyToMessageId,
        boolean isEdited,
        boolean isDeleted,
        Instant createdAt,
        Instant updatedAt
) {
    public static MessageDTO fromEntity(Message m) {
        if (m == null) return null;
        var sender = m.getSender();
        var conv = m.getConversation();
        return new MessageDTO(
                m.getId(),
                conv != null ? conv.getId() : null,
                sender != null ? sender.getId() : null,
                sender != null ? sender.getUsername() : null,
                sender != null ? sender.getFullName() : null,
                sender != null ? sender.getAvatarUrl() : null,
                m.getMessageType(),
                m.isDeleted() ? "Tin nhắn đã bị thu hồi" : m.getContent(),
                m.getReplyToMessage() != null ? m.getReplyToMessage().getId() : null,
                m.isEdited(),
                m.isDeleted(),
                m.getCreatedAt(),
                m.getUpdatedAt()
        );
    }
}
