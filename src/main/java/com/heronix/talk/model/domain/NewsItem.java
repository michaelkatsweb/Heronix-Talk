package com.heronix.talk.model.domain;

import com.heronix.talk.model.enums.SyncStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * News ticker item for announcements and updates.
 * Displayed in the communication hub news ticker section.
 */
@Entity
@Table(name = "news_items", indexes = {
        @Index(name = "idx_news_priority", columnList = "priority"),
        @Index(name = "idx_news_active", columnList = "active"),
        @Index(name = "idx_news_expiry", columnList = "expiresAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 200)
    private String headline;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    @Size(max = 100)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Builder.Default
    private int priority = 0;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private boolean pinned = false;

    @Builder.Default
    private boolean urgent = false;

    private LocalDateTime publishedAt;

    private LocalDateTime expiresAt;

    @Size(max = 500)
    private String linkUrl;

    @Size(max = 500)
    private String imagePath;

    // View tracking
    @Builder.Default
    private int viewCount = 0;

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
        if (publishedAt == null) {
            publishedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        modifiedDate = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isVisible() {
        return active && !isExpired();
    }

    public void incrementViewCount() {
        this.viewCount++;
    }
}
