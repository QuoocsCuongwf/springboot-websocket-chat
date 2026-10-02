package com.quoocscuongwf.nextchat.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @EntityGraph(attributePaths = {"participants", "participants.user", "createdBy", "lastMessage", "lastMessage.sender"})
    @Query("""
        SELECT DISTINCT c FROM Conversation c
        JOIN c.participants p
        WHERE p.user.id = :userId AND p.leftAt IS NULL
        ORDER BY c.updatedAt DESC
    """)
    List<Conversation> findRoomsByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT cp.conversation.id, COUNT(m)
        FROM ConversationParticipant cp
        LEFT JOIN Message m ON m.conversation.id = cp.conversation.id
            AND m.deleted = false
            AND m.sender.id <> :userId
            AND (cp.lastReadMessageId IS NULL OR m.id > cp.lastReadMessageId)
        WHERE cp.user.id = :userId AND cp.leftAt IS NULL
        GROUP BY cp.conversation.id
    """)
    List<Object[]> findUnreadCountsByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT get_or_create_direct_room(:userA, :userB)", nativeQuery = true)
    Long callGetOrCreateDirectRoom(@Param("userA") Long userA, @Param("userB") Long userB);
}
