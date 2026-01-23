package com.heronix.talk.model.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tracks channel membership with additional metadata.
 * Stores user-specific channel settings like mute status, last read message, etc.
 */
@Entity
@Table(name = "channel_memberships", indexes = {
        @Index(name = "idx_membership_user", columnList = "user_id"),
        @Index(name = "idx_membership_channel", columnList = "channel_id"),
        // Composite index for active memberships by channel (critical for broadcasts)
        @Index(name = "idx_membership_channel_active", columnList = "channel_id, active"),
        // Index for user's active channels
        @Index(name = "idx_membership_user_active", columnList = "user_id, active")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "channel_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @Builder.Default
    private boolean muted = false;

    @Builder.Default
    private boolean pinned = false;

    @Builder.Default
    private boolean favorite = false;

    private Long lastReadMessageId;

    private LocalDateTime lastReadTime;

    @Builder.Default
    private int unreadCount = 0;

    @Builder.Default
    private boolean canPost = true;

    @Builder.Default
    private boolean isAdmin = false;

    @Builder.Default
    private boolean isModerator = false;

    private LocalDateTime joinedAt;

    private LocalDateTime leftAt;

    @Builder.Default
    private boolean active = true;

    // Notification preferences for this channel
    @Builder.Default
    private boolean notifyOnMessage = true;

    @Builder.Default
    private boolean notifyOnMention = true;

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
    }

    public void markAsRead(Long messageId) {
        this.lastReadMessageId = messageId;
        this.lastReadTime = LocalDateTime.now();
        this.unreadCount = 0;
    }

    public void incrementUnread() {
        this.unreadCount++;
    }

    public void leave() {
        this.active = false;
        this.leftAt = LocalDateTime.now();
    }

    public void rejoin() {
        this.active = true;
        this.leftAt = null;
        this.joinedAt = LocalDateTime.now();
    }
}
