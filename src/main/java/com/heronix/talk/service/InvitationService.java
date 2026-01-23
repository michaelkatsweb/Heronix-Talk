package com.heronix.talk.service;

import com.heronix.talk.model.domain.Channel;
import com.heronix.talk.model.domain.ChannelInvitation;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.ChannelInvitationDTO;
import com.heronix.talk.model.enums.InvitationStatus;
import com.heronix.talk.repository.ChannelInvitationRepository;
import com.heronix.talk.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing channel invitations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationService {

    private final ChannelInvitationRepository invitationRepository;
    private final ChannelService channelService;
    private final UserService userService;
    private final ChatWebSocketHandler webSocketHandler;

    /**
     * Create a new channel invitation
     */
    @Transactional
    public ChannelInvitationDTO createInvitation(Long channelId, Long inviterId, Long inviteeId, String message) {
        // Validate channel exists
        Optional<Channel> channelOpt = channelService.findById(channelId);
        if (channelOpt.isEmpty()) {
            log.warn("Cannot create invitation: channel {} not found", channelId);
            throw new IllegalArgumentException("Channel not found");
        }

        // Validate inviter exists
        Optional<User> inviterOpt = userService.findById(inviterId);
        if (inviterOpt.isEmpty()) {
            log.warn("Cannot create invitation: inviter {} not found", inviterId);
            throw new IllegalArgumentException("Inviter not found");
        }

        // Validate invitee exists
        Optional<User> inviteeOpt = userService.findById(inviteeId);
        if (inviteeOpt.isEmpty()) {
            log.warn("Cannot create invitation: invitee {} not found", inviteeId);
            throw new IllegalArgumentException("Invitee not found");
        }

        Channel channel = channelOpt.get();
        User inviter = inviterOpt.get();
        User invitee = inviteeOpt.get();

        // Check if inviter is a member of the channel
        if (!channelService.isMember(channelId, inviterId)) {
            log.warn("Cannot create invitation: inviter {} is not a member of channel {}", inviterId, channelId);
            throw new IllegalArgumentException("Inviter is not a member of this channel");
        }

        // Check if invitee is already a member
        if (channelService.isMember(channelId, inviteeId)) {
            log.info("Invitee {} is already a member of channel {}", inviteeId, channelId);
            throw new IllegalArgumentException("User is already a member of this channel");
        }

        // Check if there's already a pending invitation
        if (invitationRepository.existsPendingByChannelAndInvitee(channelId, inviteeId)) {
            log.info("Pending invitation already exists for user {} in channel {}", inviteeId, channelId);
            throw new IllegalArgumentException("Invitation already pending for this user");
        }

        // Create the invitation
        ChannelInvitation invitation = ChannelInvitation.builder()
                .channel(channel)
                .inviter(inviter)
                .invitee(invitee)
                .message(message)
                .status(InvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        invitation = invitationRepository.save(invitation);
        log.info("Created invitation {} for user {} to channel {} by {}",
                invitation.getId(), inviteeId, channelId, inviterId);

        ChannelInvitationDTO dto = ChannelInvitationDTO.fromEntity(invitation);

        // Send real-time notification to invitee
        webSocketHandler.sendNotificationToUser(inviteeId, dto, "INVITE_RECEIVED");

        return dto;
    }

    /**
     * Accept an invitation
     */
    @Transactional
    public ChannelInvitationDTO acceptInvitation(Long invitationId, Long userId) {
        Optional<ChannelInvitation> invitationOpt = invitationRepository.findById(invitationId);
        if (invitationOpt.isEmpty()) {
            log.warn("Invitation {} not found", invitationId);
            throw new IllegalArgumentException("Invitation not found");
        }

        ChannelInvitation invitation = invitationOpt.get();

        // Verify the user is the invitee
        if (!invitation.getInvitee().getId().equals(userId)) {
            log.warn("User {} is not the invitee for invitation {}", userId, invitationId);
            throw new IllegalArgumentException("Not authorized to respond to this invitation");
        }

        // Check if already responded
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            log.warn("Invitation {} has already been responded to: {}", invitationId, invitation.getStatus());
            throw new IllegalArgumentException("Invitation already " + invitation.getStatus().name().toLowerCase());
        }

        // Check if expired
        if (invitation.isExpired()) {
            invitation.markExpired();
            invitationRepository.save(invitation);
            throw new IllegalArgumentException("Invitation has expired");
        }

        // Accept the invitation
        invitation.accept();
        invitationRepository.save(invitation);

        // Add user to the channel
        Channel channel = invitation.getChannel();
        User invitee = invitation.getInvitee();
        channelService.addMember(channel, invitee, false, false);

        log.info("User {} accepted invitation {} and joined channel {}",
                userId, invitationId, channel.getId());

        ChannelInvitationDTO dto = ChannelInvitationDTO.fromEntity(invitation);

        // Notify inviter that invitation was accepted
        webSocketHandler.sendNotificationToUser(
                invitation.getInviter().getId(), dto, "INVITE_ACCEPTED");

        return dto;
    }

    /**
     * Decline an invitation
     */
    @Transactional
    public ChannelInvitationDTO declineInvitation(Long invitationId, Long userId) {
        Optional<ChannelInvitation> invitationOpt = invitationRepository.findById(invitationId);
        if (invitationOpt.isEmpty()) {
            log.warn("Invitation {} not found", invitationId);
            throw new IllegalArgumentException("Invitation not found");
        }

        ChannelInvitation invitation = invitationOpt.get();

        // Verify the user is the invitee
        if (!invitation.getInvitee().getId().equals(userId)) {
            log.warn("User {} is not the invitee for invitation {}", userId, invitationId);
            throw new IllegalArgumentException("Not authorized to respond to this invitation");
        }

        // Check if already responded
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            log.warn("Invitation {} has already been responded to: {}", invitationId, invitation.getStatus());
            throw new IllegalArgumentException("Invitation already " + invitation.getStatus().name().toLowerCase());
        }

        // Decline the invitation
        invitation.decline();
        invitationRepository.save(invitation);

        log.info("User {} declined invitation {} for channel {}",
                userId, invitationId, invitation.getChannel().getId());

        ChannelInvitationDTO dto = ChannelInvitationDTO.fromEntity(invitation);

        // Notify inviter that invitation was declined
        webSocketHandler.sendNotificationToUser(
                invitation.getInviter().getId(), dto, "INVITE_DECLINED");

        return dto;
    }

    /**
     * Get pending invitations for a user
     */
    public List<ChannelInvitationDTO> getPendingInvitations(Long userId) {
        List<ChannelInvitation> invitations = invitationRepository.findPendingForUser(
                userId, LocalDateTime.now());
        return invitations.stream()
                .map(ChannelInvitationDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Count pending invitations for a user
     */
    public long countPendingInvitations(Long userId) {
        return invitationRepository.countPendingForUser(userId, LocalDateTime.now());
    }

    /**
     * Get invitation by ID
     */
    public Optional<ChannelInvitationDTO> getInvitation(Long invitationId) {
        return invitationRepository.findById(invitationId)
                .map(ChannelInvitationDTO::fromEntity);
    }

    /**
     * Get invitations sent by a user
     */
    public List<ChannelInvitationDTO> getSentInvitations(Long userId) {
        return invitationRepository.findByInviterIdOrderByCreatedAtDesc(userId).stream()
                .map(ChannelInvitationDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get all invitations for a channel
     */
    public List<ChannelInvitationDTO> getChannelInvitations(Long channelId) {
        return invitationRepository.findByChannelIdOrderByCreatedAtDesc(channelId).stream()
                .map(ChannelInvitationDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Cancel a pending invitation (by inviter)
     */
    @Transactional
    public void cancelInvitation(Long invitationId, Long inviterId) {
        Optional<ChannelInvitation> invitationOpt = invitationRepository.findById(invitationId);
        if (invitationOpt.isEmpty()) {
            throw new IllegalArgumentException("Invitation not found");
        }

        ChannelInvitation invitation = invitationOpt.get();

        // Verify the user is the inviter
        if (!invitation.getInviter().getId().equals(inviterId)) {
            throw new IllegalArgumentException("Not authorized to cancel this invitation");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalArgumentException("Cannot cancel - invitation already " +
                    invitation.getStatus().name().toLowerCase());
        }

        invitation.cancel();
        invitationRepository.save(invitation);

        log.info("Inviter {} cancelled invitation {} for channel {}",
                inviterId, invitationId, invitation.getChannel().getId());

        // Notify invitee that invitation was cancelled
        webSocketHandler.sendNotificationToUser(
                invitation.getInvitee().getId(),
                ChannelInvitationDTO.fromEntity(invitation),
                "INVITE_CANCELLED");
    }

    /**
     * Scheduled task to expire old invitations
     */
    @Scheduled(cron = "0 0 * * * *") // Run every hour
    @Transactional
    public void expireOldInvitations() {
        List<ChannelInvitation> expired = invitationRepository.findExpiredInvitations(LocalDateTime.now());
        for (ChannelInvitation invitation : expired) {
            invitation.markExpired();
            invitationRepository.save(invitation);
            log.info("Expired invitation {} for user {} to channel {}",
                    invitation.getId(), invitation.getInvitee().getId(), invitation.getChannel().getId());
        }
        if (!expired.isEmpty()) {
            log.info("Expired {} old invitations", expired.size());
        }
    }
}
