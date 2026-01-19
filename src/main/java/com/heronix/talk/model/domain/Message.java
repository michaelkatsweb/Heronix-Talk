package com.heronix.talk.model.domain;

import com.heronix.talk.model.enums.MessageStatus;
import com.heronix.talk.model.enums.MessageType;
import com.heronix.talk.model.enums.SyncStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Message entity representing individual chat messages.
 * Supports text, files, images, replies, and reactions.
 */
@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_message_channel", columnList = "channel_id"),
        @Index(name = "idx_message_sender", columnList = "sender_id"),
        @Index(name = "idx_message_timestamp", columnList = "timestamp"),
        @Index(name = "idx_message_uuid", columnList = "messageUuid", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 36)
    private String messageUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    @NotNull
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    @NotNull
    private User sender;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MessageStatus status = MessageStatus.SENT;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private LocalDateTime editedAt;

    @Builder.Default
    private boolean edited = false;

    @Builder.Default
    private boolean deleted = false;

    @Builder.Default
    private boolean pinned = false;

    @Builder.Default
    private boolean important = false;

    // Reply support
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    private Message replyTo;

    @OneToMany(mappedBy = "replyTo", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Message> replies = new HashSet<>();

    // Thread support
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_root_id")
    private Message threadRoot;

    @Builder.Default
    private int replyCount = 0;

    // File attachment fields
    @Size(max = 500)
    private String attachmentPath;

    @Size(max = 255)
    private String attachmentName;

    @Size(max = 100)
    private String attachmentType;

    private Long attachmentSize;

    // Reactions stored as JSON string: {"thumbsUp": [1,2,3], "heart": [1,5]}
    @Lob
    @Column(columnDefinition = "TEXT")
    private String reactions;

    // Mentions stored as JSON array of user IDs
    @Size(max = 1000)
    private String mentions;

    // Read receipts - JSON object of userId: timestamp
    @Lob
    @Column(columnDefinition = "TEXT")
    private String readReceipts;

    // Sync fields
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.PENDING;

    private LocalDateTime lastSyncTime;

    // Client-side tracking
    @Size(max = 100)
    private String clientId;

    @PrePersist
    protected void onCreate() {
        if (messageUuid == null) {
            messageUuid = UUID.randomUUID().toString();
        }
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (messageType == null) {
            messageType = MessageType.TEXT;
        }
        if (status == null) {
            status = MessageStatus.SENT;
        }
        if (syncStatus == null) {
            syncStatus = SyncStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (syncStatus == SyncStatus.SYNCED) {
            syncStatus = SyncStatus.PENDING;
        }
    }

    public void markAsEdited() {
        this.edited = true;
        this.editedAt = LocalDateTime.now();
    }

    public void markAsDeleted() {
        this.deleted = true;
        this.content = "[Message deleted]";
        this.messageType = MessageType.DELETED;
    }

    public boolean hasAttachment() {
        return attachmentPath != null && !attachmentPath.isEmpty();
    }

    public boolean isReply() {
        return replyTo != null;
    }

    public boolean isThreaded() {
        return threadRoot != null;
    }

    public String getSenderName() {
        return sender != null ? sender.getFullName() : "Unknown";
    }

    public String getPreview() {
        if (deleted) {
            return "[Deleted]";
        }
        if (content == null || content.isEmpty()) {
            if (hasAttachment()) {
                return "[Attachment: " + attachmentName + "]";
            }
            return "[Empty message]";
        }
        return content.length() > 100 ? content.substring(0, 97) + "..." : content;
    }
}
