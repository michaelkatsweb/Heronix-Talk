package com.heronix.talk.model.domain;

import com.heronix.talk.model.enums.SyncStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * File attachment entity for messages.
 * Stores file metadata and path for offline-first file handling.
 */
@Entity
@Table(name = "attachments", indexes = {
        @Index(name = "idx_attachment_message", columnList = "message_id"),
        @Index(name = "idx_attachment_uuid", columnList = "attachmentUuid", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 36)
    private String attachmentUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @NotBlank
    @Size(max = 255)
    private String originalFileName;

    @NotBlank
    @Size(max = 500)
    private String storagePath;

    @Size(max = 100)
    private String contentType;

    private Long fileSize;

    @Size(max = 500)
    private String thumbnailPath;

    @Builder.Default
    private boolean processed = false;

    @Builder.Default
    private boolean deleted = false;

    // For images
    private Integer width;
    private Integer height;

    // For videos/audio
    private Integer durationSeconds;

    // Checksum for integrity verification
    @Size(max = 64)
    private String checksum;

    // Sync fields
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.LOCAL_ONLY;

    private LocalDateTime lastSyncTime;

    // Audit fields
    @Column(updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    protected void onCreate() {
        if (attachmentUuid == null) {
            attachmentUuid = UUID.randomUUID().toString();
        }
        createdDate = LocalDateTime.now();
        if (syncStatus == null) {
            syncStatus = SyncStatus.LOCAL_ONLY;
        }
    }

    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }

    public boolean isVideo() {
        return contentType != null && contentType.startsWith("video/");
    }

    public boolean isAudio() {
        return contentType != null && contentType.startsWith("audio/");
    }

    public boolean isDocument() {
        return contentType != null && (
                contentType.equals("application/pdf") ||
                contentType.contains("document") ||
                contentType.contains("spreadsheet") ||
                contentType.contains("presentation")
        );
    }

    public String getFileSizeFormatted() {
        if (fileSize == null) return "Unknown";
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        if (fileSize < 1024 * 1024 * 1024) return String.format("%.1f MB", fileSize / (1024.0 * 1024));
        return String.format("%.1f GB", fileSize / (1024.0 * 1024 * 1024));
    }
}
