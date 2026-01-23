package com.heronix.talk.repository;

import com.heronix.talk.model.domain.Message;
import com.heronix.talk.model.enums.MessageType;
import com.heronix.talk.model.enums.SyncStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Message entity operations.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findByMessageUuid(String messageUuid);

    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.channel LEFT JOIN FETCH m.sender WHERE m.channel.id = :channelId AND m.deleted = false ORDER BY m.timestamp DESC")
    Page<Message> findByChannelIdOrderByTimestampDesc(@Param("channelId") Long channelId, Pageable pageable);

    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.channel LEFT JOIN FETCH m.sender WHERE m.channel.id = :channelId AND m.deleted = false ORDER BY m.timestamp ASC")
    List<Message> findByChannelIdOrderByTimestampAsc(@Param("channelId") Long channelId);

    @Query("SELECT m FROM Message m WHERE m.channel.id = :channelId AND m.timestamp > :since AND m.deleted = false ORDER BY m.timestamp ASC")
    List<Message> findByChannelIdAndTimestampAfter(@Param("channelId") Long channelId, @Param("since") LocalDateTime since);

    @Query("SELECT m FROM Message m WHERE m.channel.id = :channelId AND m.id > :messageId AND m.deleted = false ORDER BY m.timestamp ASC")
    List<Message> findByChannelIdAndIdGreaterThan(@Param("channelId") Long channelId, @Param("messageId") Long messageId);

    @Query("SELECT m FROM Message m WHERE m.sender.id = :senderId AND m.deleted = false ORDER BY m.timestamp DESC")
    Page<Message> findBySenderId(@Param("senderId") Long senderId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.channel.id = :channelId AND m.pinned = true AND m.deleted = false ORDER BY m.timestamp DESC")
    List<Message> findPinnedMessagesByChannelId(@Param("channelId") Long channelId);

    @Query("SELECT m FROM Message m WHERE m.replyTo.id = :messageId AND m.deleted = false ORDER BY m.timestamp ASC")
    List<Message> findReplies(@Param("messageId") Long messageId);

    @Query("SELECT m FROM Message m WHERE m.threadRoot.id = :threadRootId AND m.deleted = false ORDER BY m.timestamp ASC")
    List<Message> findThreadMessages(@Param("threadRootId") Long threadRootId);

    @Query(value = "SELECT * FROM messages m WHERE m.channel_id = :channelId AND m.deleted = false AND " +
            "LOWER(m.content) LIKE LOWER(CONCAT('%', :term, '%')) ORDER BY m.timestamp DESC",
            countQuery = "SELECT COUNT(*) FROM messages m WHERE m.channel_id = :channelId AND m.deleted = false AND " +
            "LOWER(m.content) LIKE LOWER(CONCAT('%', :term, '%'))",
            nativeQuery = true)
    Page<Message> searchInChannel(@Param("channelId") Long channelId, @Param("term") String term, Pageable pageable);

    @Query(value = "SELECT * FROM messages m WHERE m.deleted = false AND m.content IS NOT NULL AND " +
            "LOWER(m.content) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY m.timestamp DESC",
            countQuery = "SELECT COUNT(*) FROM messages m WHERE m.deleted = false AND m.content IS NOT NULL AND " +
            "LOWER(m.content) LIKE LOWER(CONCAT('%', :searchTerm, '%'))",
            nativeQuery = true)
    Page<Message> searchAll(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.syncStatus = :status")
    List<Message> findBySyncStatus(@Param("status") SyncStatus status);

    @Query("SELECT m FROM Message m WHERE m.syncStatus IN ('PENDING', 'LOCAL_ONLY')")
    List<Message> findNeedingSync();

    @Query("SELECT m FROM Message m WHERE m.messageType = :type AND m.deleted = false ORDER BY m.timestamp DESC")
    Page<Message> findByMessageType(@Param("type") MessageType type, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.important = true AND m.deleted = false ORDER BY m.timestamp DESC")
    List<Message> findImportantMessages();

    @Query("SELECT m FROM Message m WHERE m.channel.id = :channelId AND m.important = true AND m.deleted = false")
    List<Message> findImportantMessagesByChannelId(@Param("channelId") Long channelId);

    @Modifying
    @Query("UPDATE Message m SET m.deleted = true, m.content = '[Message deleted]', m.messageType = 'DELETED' WHERE m.id = :messageId")
    void softDelete(@Param("messageId") Long messageId);

    @Modifying
    @Query("UPDATE Message m SET m.pinned = :pinned WHERE m.id = :messageId")
    void updatePinned(@Param("messageId") Long messageId, @Param("pinned") boolean pinned);

    @Modifying
    @Query("UPDATE Message m SET m.replyCount = m.replyCount + 1 WHERE m.id = :messageId")
    void incrementReplyCount(@Param("messageId") Long messageId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.channel.id = :channelId AND m.deleted = false")
    long countByChannelId(@Param("channelId") Long channelId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.channel.id = :channelId AND m.id > :afterId AND m.deleted = false")
    long countUnreadInChannel(@Param("channelId") Long channelId, @Param("afterId") Long afterId);

    @Query("SELECT MAX(m.id) FROM Message m WHERE m.channel.id = :channelId AND m.deleted = false")
    Optional<Long> findLastMessageIdInChannel(@Param("channelId") Long channelId);

    boolean existsByClientId(String clientId);
}
