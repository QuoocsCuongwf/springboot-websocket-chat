package com.quoocscuongwf.nextchat.room;

import com.quoocscuongwf.nextchat.room.dto.*;
import com.quoocscuongwf.nextchat.user.User;
import com.quoocscuongwf.nextchat.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class RoomService {
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public RoomService(
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            MessageRepository messageRepository,
            UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomDTO> getUserRooms(Long userId) {
        List<Conversation> conversations = conversationRepository.findRoomsByUserId(userId);
        Map<Long, Long> unreadCounts = new HashMap<>();
        for (Object[] row : conversationRepository.findUnreadCountsByUserId(userId)) {
            unreadCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return conversations.stream()
                .map(c -> RoomDTO.fromEntity(c, userId, unreadCounts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public RoomDTO getOrCreateDirectRoom(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot create a direct chat with yourself");
        }

        User currentUser = userRepository.findById(currentUserId)
                .filter(u -> u.isActive() && !u.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Current user not found"));

        User targetUser = userRepository.findById(targetUserId)
                .filter(u -> u.isActive() && !u.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found or inactive"));

        Long roomId = conversationRepository.callGetOrCreateDirectRoom(currentUserId, targetUserId);
        if (roomId == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Room creation failed");
        }
        return conversationRepository.findById(roomId)
                .map(c -> RoomDTO.fromEntity(c, currentUserId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Room creation failed"));
    }

    @Transactional
    public RoomDTO createGroupRoom(Long currentUserId, String groupName, List<Long> memberIds) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Current user not found"));

        Set<Long> uniqueMemberIds = new LinkedHashSet<>(memberIds != null ? memberIds : List.of());
        uniqueMemberIds.remove(currentUserId);
        if (uniqueMemberIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A group requires at least one other member");
        }

        List<User> members = userRepository.findAllById(uniqueMemberIds);
        if (members.size() != uniqueMemberIds.size()
                || members.stream().anyMatch(member -> !member.isActive() || member.isDeleted())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more group members were not found or are inactive");
        }

        Conversation group = new Conversation("GROUP", groupName.trim(), currentUser);
        final Conversation savedGroup = conversationRepository.save(group);

        ConversationParticipant ownerParticipant = new ConversationParticipant(savedGroup, currentUser, "OWNER");
        participantRepository.save(ownerParticipant);
        savedGroup.addParticipant(ownerParticipant);

        for (User member : members) {
            ConversationParticipant cp = new ConversationParticipant(savedGroup, member, "MEMBER");
            participantRepository.save(cp);
            savedGroup.addParticipant(cp);
        }

        return RoomDTO.fromEntity(savedGroup, currentUserId);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Page<MessageDTO> getRoomMessages(Long currentUserId, Long roomId, int page, int size) {
        if (!conversationRepository.existsById(roomId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
        }

        boolean isMember = participantRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(roomId, currentUserId);
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a participant in this room");
        }

        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be between 1 and 100");
        }
        int pageNum = page;
        int pageSize = size;

        // Optimized query using PLpgSQL stored function: get_room_messages(p_room_id, p_page, p_size)
        String sql = "SELECT id, conversation_id, sender_id, sender_username, sender_full_name, sender_avatar_url, " +
                "message_type, content, reply_to_message_id, is_edited, is_deleted, created_at, updated_at, total_count " +
                "FROM get_room_messages(:roomId, :page, :size)";

        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("roomId", roomId)
                .setParameter("page", pageNum)
                .setParameter("size", pageSize)
                .getResultList();

        long totalCount = rows.isEmpty()
                ? messageRepository.countByConversationIdAndDeletedFalse(roomId)
                : 0L;
        List<MessageDTO> dtoList = new ArrayList<>();

        for (Object[] row : rows) {
                Long id = ((Number) row[0]).longValue();
                Long convId = ((Number) row[1]).longValue();
                Long senderId = ((Number) row[2]).longValue();
                String senderUsername = (String) row[3];
                String senderFullName = (String) row[4];
                String senderAvatarUrl = (String) row[5];
                String messageType = (String) row[6];
                String content = (String) row[7];
                Long replyToId = row[8] != null ? ((Number) row[8]).longValue() : null;
                boolean isEdited = row[9] != null && (Boolean) row[9];
                boolean isDeleted = row[10] != null && (Boolean) row[10];

                Instant createdAt = toInstant(row[11]);
                Instant updatedAt = toInstant(row[12]);
                totalCount = row[13] != null ? ((Number) row[13]).longValue() : 0L;

                dtoList.add(new MessageDTO(
                        id,
                        convId,
                        senderId,
                        senderUsername,
                        senderFullName,
                        senderAvatarUrl,
                        messageType,
                        content,
                        replyToId,
                        isEdited,
                        isDeleted,
                        createdAt,
                        updatedAt
                ));
        }

        return new PageImpl<>(dtoList, PageRequest.of(pageNum, pageSize), totalCount);
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        } else if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        } else if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        } else if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        } else if (value instanceof Date date) {
            return date.toInstant();
        }
        throw new IllegalStateException("Unexpected timestamp value returned from PostgreSQL: " + value);
    }
}
