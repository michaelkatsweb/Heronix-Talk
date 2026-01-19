package com.heronix.talk.repository;

import com.heronix.talk.model.domain.Attachment;
import com.heronix.talk.model.enums.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Attachment entity operations.
 */
@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    Optional<Attachment> findByAttachmentUuid(String attachmentUuid);

    List<Attachment> findByMessageId(Long messageId);

    List<Attachment> findByUploaderId(Long uploaderId);

    @Query("SELECT a FROM Attachment a WHERE a.syncStatus = :status AND a.deleted = false")
    List<Attachment> findBySyncStatus(@Param("status") SyncStatus status);

    @Query("SELECT a FROM Attachment a WHERE a.syncStatus IN ('PENDING', 'LOCAL_ONLY') AND a.deleted = false")
    List<Attachment> findNeedingSync();

    @Query("SELECT a FROM Attachment a WHERE a.contentType LIKE 'image/%' AND a.deleted = false")
    List<Attachment> findImages();

    @Query("SELECT a FROM Attachment a WHERE a.message.channel.id = :channelId AND a.deleted = false ORDER BY a.createdDate DESC")
    List<Attachment> findByChannelId(@Param("channelId") Long channelId);

    @Query("SELECT SUM(a.fileSize) FROM Attachment a WHERE a.uploader.id = :userId AND a.deleted = false")
    Long getTotalFileSizeByUploader(@Param("userId") Long userId);

    long countByUploaderId(Long uploaderId);

    long countByMessageId(Long messageId);
}
