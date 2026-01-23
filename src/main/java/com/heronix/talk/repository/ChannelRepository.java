package com.heronix.talk.repository;

import com.heronix.talk.model.domain.Channel;
import com.heronix.talk.model.enums.ChannelType;
import com.heronix.talk.model.enums.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Channel entity operations.
 */
@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {

    List<Channel> findByActiveTrue();

    List<Channel> findByActiveTrueOrderByLastMessageTimeDesc();

    List<Channel> findByChannelType(ChannelType channelType);

    List<Channel> findByChannelTypeAndActiveTrue(ChannelType channelType);

    Optional<Channel> findByDirectMessageKey(String directMessageKey);

    @Query("SELECT c FROM Channel c WHERE c.channelType = 'PUBLIC' AND c.active = true ORDER BY c.name")
    List<Channel> findPublicChannels();

    @Query("SELECT c FROM Channel c WHERE c.channelType = 'ANNOUNCEMENT' AND c.active = true ORDER BY c.createdDate DESC")
    List<Channel> findAnnouncementChannels();

    @Query("SELECT c FROM Channel c WHERE c.id IN (SELECT cm.channel.id FROM ChannelMembership cm WHERE cm.user.id = :userId AND cm.active = true) AND c.active = true ORDER BY c.lastMessageTime DESC")
    List<Channel> findChannelsByMemberId(@Param("userId") Long userId);

    @Query("SELECT c FROM Channel c WHERE c.id IN (SELECT cm.channel.id FROM ChannelMembership cm WHERE cm.user.id = :userId AND cm.active = true) AND c.channelType = :type AND c.active = true")
    List<Channel> findChannelsByMemberIdAndType(@Param("userId") Long userId, @Param("type") ChannelType type);

    @Query("SELECT c FROM Channel c WHERE c.syncStatus = :status")
    List<Channel> findBySyncStatus(@Param("status") SyncStatus status);

    @Query("SELECT c FROM Channel c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :term, '%')) AND c.active = true")
    List<Channel> searchByName(@Param("term") String term);

    @Query("SELECT c FROM Channel c WHERE c.pinned = true AND c.active = true ORDER BY c.name")
    List<Channel> findPinnedChannels();

    @Query("SELECT c FROM Channel c WHERE c.archived = true ORDER BY c.modifiedDate DESC")
    List<Channel> findArchivedChannels();

    @Query("SELECT COUNT(c) FROM Channel c WHERE c.channelType = :type AND c.active = true")
    long countByType(@Param("type") ChannelType type);

    boolean existsByNameAndChannelType(String name, ChannelType channelType);

    boolean existsByDirectMessageKey(String directMessageKey);

    long countByArchivedFalse();

    long countByActiveTrueAndArchivedFalse();
}
