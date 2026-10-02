package com.quoocscuongwf.nextchat.room;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    long countByConversationIdAndDeletedFalse(Long conversationId);

    @Query("""
        SELECT m FROM Message m
        JOIN FETCH m.sender s
        WHERE m.conversation.id = :roomId AND m.deleted = false
        ORDER BY m.createdAt DESC
    """)
    Page<Message> findByRoomIdOrderByCreatedAtDesc(@Param("roomId") Long roomId, Pageable pageable);
}
