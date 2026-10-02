package com.quoocscuongwf.nextchat.room;

import com.quoocscuongwf.nextchat.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "conversation_participants",
        uniqueConstraints = @UniqueConstraint(name = "uq_conversation_user", columnNames = {"conversation_id", "user_id"}))
public class ConversationParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 10)
    private String role = "MEMBER"; // 'OWNER', 'ADMIN', 'MEMBER'

    @Column(length = 100)
    private String nickname;

    @Column(name = "is_muted", nullable = false)
    private boolean muted = false;

    @Column(name = "mute_until")
    private Instant muteUntil;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned = false;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    protected ConversationParticipant() {}

    public ConversationParticipant(Conversation conversation, User user, String role) {
        this.conversation = conversation;
        this.user = user;
        this.role = role != null ? role : "MEMBER";
    }

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
        if (role == null) {
            role = "MEMBER";
        }
    }

    public Long getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public void setConversation(Conversation conversation) { this.conversation = conversation; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }
    public Instant getMuteUntil() { return muteUntil; }
    public void setMuteUntil(Instant muteUntil) { this.muteUntil = muteUntil; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public Long getLastReadMessageId() { return lastReadMessageId; }
    public void setLastReadMessageId(Long lastReadMessageId) { this.lastReadMessageId = lastReadMessageId; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getLeftAt() { return leftAt; }
    public void setLeftAt(Instant leftAt) { this.leftAt = leftAt; }
}
