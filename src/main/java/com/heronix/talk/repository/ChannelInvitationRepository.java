package com.heronix.talk.repository;

import com.heronix.talk.model.domain.ChannelInvitation;
import com.heronix.talk.model.enums.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ChannelInvitation entity operations.
 */
@Repository
public interface ChannelInvitationRepository extends JpaRepository<ChannelInvitation, Long> {

    /**
     * Find all pending invitations for a user
     */
    @Query("SELECT ci FROM ChannelInvitation ci " +
           "WHERE ci.invitee.id = :userId " +
           "AND ci.status = 'PENDING' " +
           "AND (ci.expiresAt IS NULL OR ci.expiresAt > :now) " +
           "ORDER BY ci.createdAt DESC")
    List<ChannelInvitation> findPendingForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Find all invitations for a user (any status)
     */
    List<ChannelInvitation> findByInviteeIdOrderByCreatedAtDesc(Long inviteeId);

    /**
     * Find invitations by inviter
     */
    List<ChannelInvitation> findByInviterIdOrderByCreatedAtDesc(Long inviterId);

    /**
     * Find invitations by channel
     */
    List<ChannelInvitation> findByChannelIdOrderByCreatedAtDesc(Long channelId);

    /**
     * Check if an invitation already exists for a user in a channel
     */
    @Query("SELECT CASE WHEN COUNT(ci) > 0 THEN true ELSE false END " +
           "FROM ChannelInvitation ci " +
           "WHERE ci.channel.id = :channelId " +
           "AND ci.invitee.id = :inviteeId " +
           "AND ci.status = 'PENDING'")
    boolean existsPendingByChannelAndInvitee(@Param("channelId") Long channelId, @Param("inviteeId") Long inviteeId);

    /**
     * Find pending invitation for a specific channel and user
     */
    @Query("SELECT ci FROM ChannelInvitation ci " +
           "WHERE ci.channel.id = :channelId " +
           "AND ci.invitee.id = :inviteeId " +
           "AND ci.status = 'PENDING'")
    Optional<ChannelInvitation> findPendingByChannelAndInvitee(@Param("channelId") Long channelId, @Param("inviteeId") Long inviteeId);

    /**
     * Count pending invitations for a user
     */
    @Query("SELECT COUNT(ci) FROM ChannelInvitation ci " +
           "WHERE ci.invitee.id = :userId " +
           "AND ci.status = 'PENDING' " +
           "AND (ci.expiresAt IS NULL OR ci.expiresAt > :now)")
    long countPendingForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Find expired pending invitations
     */
    @Query("SELECT ci FROM ChannelInvitation ci " +
           "WHERE ci.status = 'PENDING' " +
           "AND ci.expiresAt IS NOT NULL " +
           "AND ci.expiresAt < :now")
    List<ChannelInvitation> findExpiredInvitations(@Param("now") LocalDateTime now);

    /**
     * Find all invitations by status
     */
    List<ChannelInvitation> findByStatus(InvitationStatus status);

    /**
     * Find recent invitations for a channel
     */
    @Query("SELECT ci FROM ChannelInvitation ci " +
           "WHERE ci.channel.id = :channelId " +
           "AND ci.createdAt > :since " +
           "ORDER BY ci.createdAt DESC")
    List<ChannelInvitation> findRecentByChannel(@Param("channelId") Long channelId, @Param("since") LocalDateTime since);
}
