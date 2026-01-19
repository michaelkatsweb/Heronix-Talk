package com.heronix.talk.repository;

import com.heronix.talk.model.domain.ChannelMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ChannelMembership entity operations.
 */
@Repository
public interface ChannelMembershipRepository extends JpaRepository<ChannelMembership, Long> {

    Optional<ChannelMembership> findByUserIdAndChannelId(Long userId, Long channelId);

    List<ChannelMembership> findByUserId(Long userId);

    List<ChannelMembership> findByUserIdAndActiveTrue(Long userId);

    List<ChannelMembership> findByChannelId(Long channelId);

    List<ChannelMembership> findByChannelIdAndActiveTrue(Long channelId);

    @Query("SELECT cm FROM ChannelMembership cm WHERE cm.user.id = :userId AND cm.muted = false AND cm.active = true")
    List<ChannelMembership> findUnmutedByUserId(@Param("userId") Long userId);

    @Query("SELECT cm FROM ChannelMembership cm WHERE cm.user.id = :userId AND cm.pinned = true AND cm.active = true")
    List<ChannelMembership> findPinnedByUserId(@Param("userId") Long userId);

    @Query("SELECT cm FROM ChannelMembership cm WHERE cm.user.id = :userId AND cm.favorite = true AND cm.active = true")
    List<ChannelMembership> findFavoritesByUserId(@Param("userId") Long userId);

    @Query("SELECT cm FROM ChannelMembership cm WHERE cm.user.id = :userId AND cm.unreadCount > 0 AND cm.active = true")
    List<ChannelMembership> findWithUnreadByUserId(@Param("userId") Long userId);

    @Query("SELECT SUM(cm.unreadCount) FROM ChannelMembership cm WHERE cm.user.id = :userId AND cm.active = true")
    Long getTotalUnreadCountByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE ChannelMembership cm SET cm.lastReadMessageId = :messageId, cm.lastReadTime = :readTime, cm.unreadCount = 0 WHERE cm.user.id = :userId AND cm.channel.id = :channelId")
    void markAsRead(@Param("userId") Long userId, @Param("channelId") Long channelId,
                    @Param("messageId") Long messageId, @Param("readTime") LocalDateTime readTime);

    @Modifying
    @Query("UPDATE ChannelMembership cm SET cm.unreadCount = cm.unreadCount + 1 WHERE cm.channel.id = :channelId AND cm.user.id != :excludeUserId AND cm.active = true")
    void incrementUnreadForChannel(@Param("channelId") Long channelId, @Param("excludeUserId") Long excludeUserId);

    @Modifying
    @Query("UPDATE ChannelMembership cm SET cm.muted = :muted WHERE cm.user.id = :userId AND cm.channel.id = :channelId")
    void updateMuted(@Param("userId") Long userId, @Param("channelId") Long channelId, @Param("muted") boolean muted);

    @Modifying
    @Query("UPDATE ChannelMembership cm SET cm.pinned = :pinned WHERE cm.user.id = :userId AND cm.channel.id = :channelId")
    void updatePinned(@Param("userId") Long userId, @Param("channelId") Long channelId, @Param("pinned") boolean pinned);

    @Modifying
    @Query("UPDATE ChannelMembership cm SET cm.favorite = :favorite WHERE cm.user.id = :userId AND cm.channel.id = :channelId")
    void updateFavorite(@Param("userId") Long userId, @Param("channelId") Long channelId, @Param("favorite") boolean favorite);

    boolean existsByUserIdAndChannelIdAndActiveTrue(Long userId, Long channelId);

    long countByChannelIdAndActiveTrue(Long channelId);

    @Query("SELECT cm.channel.id FROM ChannelMembership cm WHERE cm.user.id = :userId AND cm.active = true")
    List<Long> findChannelIdsByUserId(@Param("userId") Long userId);
}
