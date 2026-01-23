package com.heronix.talk.model.enums;

/**
 * Status of a channel invitation.
 */
public enum InvitationStatus {
    PENDING,    // Invitation sent, awaiting response
    ACCEPTED,   // User accepted the invitation
    DECLINED,   // User declined the invitation
    EXPIRED,    // Invitation expired without response
    CANCELLED   // Inviter cancelled the invitation
}
