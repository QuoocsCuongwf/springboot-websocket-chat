package com.quoocscuongwf.nextchat.room;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {
    Optional<ConversationParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);
    boolean existsByConversationIdAndUserIdAndLeftAtIsNull(Long conversationId, Long userId);
    List<ConversationParticipant> findByConversationIdAndLeftAtIsNull(Long conversationId);
}
