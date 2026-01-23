package com.heronix.talk.model.domain;

import com.heronix.talk.model.enums.InvitationStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity representing an invitation to join a channel.
 * Users receive invitations and must accept before joining private channels.
 */
@Entity
@Table(name = "channel_invitations", indexes = {
        @Index(name = "idx_invitation_invitee", columnList = "invitee_id"),
        @Index(name = "idx_invitation_channel", columnList = "channel_id"),
        @Index(name = "idx_invitation_status", columnList = "status")
})
@Getter
@Setter
@ToString(exclude = {"channel", "inviter", "invitee"})
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inviter_id", nullable = false)
    private User inviter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_id", nullable = false)
    private User invitee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    @Size(max = 500)
    private String message;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime respondedAt;

    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = InvitationStatus.PENDING;
        }
        // Default expiration: 7 days
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusDays(7);
        }
    }

    /**
     * Check if the invitation has expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if the invitation is still pending
     */
    public boolean isPending() {
        return status == InvitationStatus.PENDING && !isExpired();
    }

    /**
     * Accept the invitation
     */
    public void accept() {
        this.status = InvitationStatus.ACCEPTED;
        this.respondedAt = LocalDateTime.now();
    }

    /**
     * Decline the invitation
     */
    public void decline() {
        this.status = InvitationStatus.DECLINED;
        this.respondedAt = LocalDateTime.now();
    }

    /**
     * Cancel the invitation (by inviter)
     */
    public void cancel() {
        this.status = InvitationStatus.CANCELLED;
        this.respondedAt = LocalDateTime.now();
    }

    /**
     * Mark as expired
     */
    public void markExpired() {
        this.status = InvitationStatus.EXPIRED;
        this.respondedAt = LocalDateTime.now();
    }
}
