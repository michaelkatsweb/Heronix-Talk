package com.heronix.talk.model.domain;

import com.heronix.talk.model.enums.ChannelType;
import com.heronix.talk.model.enums.SyncStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Channel entity representing chat rooms/channels.
 * Can be public, private, department-specific, or direct messages.
 */
@Entity
@Table(name = "channels", indexes = {
        @Index(name = "idx_channel_name", columnList = "name"),
        @Index(name = "idx_channel_type", columnList = "channelType")
})
@Getter
@Setter
@ToString(exclude = {"members", "messages", "creator"})
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false)
    private String name;

    @Size(max = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ChannelType channelType = ChannelType.PUBLIC;

    @Size(max = 100)
    private String icon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @ManyToMany(mappedBy = "channels", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<User> members = new HashSet<>();

    @OneToMany(mappedBy = "channel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Message> messages = new HashSet<>();

    private LocalDateTime lastMessageTime;

    @Builder.Default
    private int memberCount = 0;

    @Builder.Default
    private int messageCount = 0;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private boolean archived = false;

    @Builder.Default
    private boolean pinned = false;

    // For direct messages - stores the two user IDs
    @Size(max = 100)
    private String directMessageKey;

    // Sync fields
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.PENDING;

    private LocalDateTime lastSyncTime;

    // Audit fields
    @Column(updatable = false)
    private LocalDateTime createdDate;

    private LocalDateTime modifiedDate;

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        modifiedDate = LocalDateTime.now();
        if (channelType == null) {
            channelType = ChannelType.PUBLIC;
        }
        if (syncStatus == null) {
            syncStatus = SyncStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedDate = LocalDateTime.now();
    }

    public void incrementMessageCount() {
        this.messageCount++;
        this.lastMessageTime = LocalDateTime.now();
    }

    public void updateMemberCount() {
        this.memberCount = members != null ? members.size() : 0;
    }

    public boolean isDirectMessage() {
        return channelType == ChannelType.DIRECT_MESSAGE;
    }

    public boolean isPublic() {
        return channelType == ChannelType.PUBLIC;
    }

    public boolean isAnnouncement() {
        return channelType == ChannelType.ANNOUNCEMENT;
    }

    public static String generateDirectMessageKey(Long userId1, Long userId2) {
        long smaller = Math.min(userId1, userId2);
        long larger = Math.max(userId1, userId2);
        return "dm_" + smaller + "_" + larger;
    }
}
