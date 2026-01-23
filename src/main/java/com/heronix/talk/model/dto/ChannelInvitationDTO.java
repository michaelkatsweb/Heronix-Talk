package com.heronix.talk.model.dto;

import com.heronix.talk.model.domain.ChannelInvitation;
import com.heronix.talk.model.enums.InvitationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for channel invitation data transfer
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelInvitationDTO {

    private Long id;

    // Channel info
    private Long channelId;
    private String channelName;
    private String channelDescription;
    private String channelType;
    private String channelIcon;
    private int channelMemberCount;

    // Inviter info
    private Long inviterId;
    private String inviterName;
    private String inviterRole;

    // Invitee info
    private Long inviteeId;
    private String inviteeName;

    // Invitation details
    private InvitationStatus status;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
    private LocalDateTime expiresAt;
    private boolean expired;

    /**
     * Create DTO from entity
     */
    public static ChannelInvitationDTO fromEntity(ChannelInvitation invitation) {
        if (invitation == null) return null;

        var channel = invitation.getChannel();
        var inviter = invitation.getInviter();
        var invitee = invitation.getInvitee();

        return ChannelInvitationDTO.builder()
                .id(invitation.getId())
                // Channel info
                .channelId(channel != null ? channel.getId() : null)
                .channelName(channel != null ? channel.getName() : null)
                .channelDescription(channel != null ? channel.getDescription() : null)
                .channelType(channel != null ? channel.getChannelType().name() : null)
                .channelIcon(channel != null ? channel.getIcon() : null)
                .channelMemberCount(channel != null ? channel.getMemberCount() : 0)
                // Inviter info
                .inviterId(inviter != null ? inviter.getId() : null)
                .inviterName(inviter != null ? inviter.getFirstName() + " " + inviter.getLastName() : null)
                .inviterRole(inviter != null && inviter.getRole() != null ? inviter.getRole().name() : null)
                // Invitee info
                .inviteeId(invitee != null ? invitee.getId() : null)
                .inviteeName(invitee != null ? invitee.getFirstName() + " " + invitee.getLastName() : null)
                // Invitation details
                .status(invitation.getStatus())
                .message(invitation.getMessage())
                .createdAt(invitation.getCreatedAt())
                .respondedAt(invitation.getRespondedAt())
                .expiresAt(invitation.getExpiresAt())
                .expired(invitation.isExpired())
                .build();
    }
}
