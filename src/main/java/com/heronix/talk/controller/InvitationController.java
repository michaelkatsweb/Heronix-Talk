package com.heronix.talk.controller;

import com.heronix.talk.model.dto.ChannelInvitationDTO;
import com.heronix.talk.service.AuthenticationService;
import com.heronix.talk.service.InvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for channel invitation operations.
 */
@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
@Slf4j
public class InvitationController {

    private final InvitationService invitationService;
    private final AuthenticationService authenticationService;

    /**
     * Get pending invitations for the current user
     */
    @GetMapping("/pending")
    public ResponseEntity<List<ChannelInvitationDTO>> getPendingInvitations(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> ResponseEntity.ok(invitationService.getPendingInvitations(user.getId())))
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Get count of pending invitations for the current user
     */
    @GetMapping("/pending/count")
    public ResponseEntity<Map<String, Long>> getPendingCount(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    long count = invitationService.countPendingInvitations(user.getId());
                    return ResponseEntity.ok(Map.of("count", count));
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Get invitations sent by the current user
     */
    @GetMapping("/sent")
    public ResponseEntity<List<ChannelInvitationDTO>> getSentInvitations(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> ResponseEntity.ok(invitationService.getSentInvitations(user.getId())))
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Get invitation by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChannelInvitationDTO> getInvitation(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .flatMap(user -> invitationService.getInvitation(id))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Invite a user to a channel
     */
    @PostMapping("/channel/{channelId}/user/{userId}")
    public ResponseEntity<ChannelInvitationDTO> inviteUser(
            @PathVariable Long channelId,
            @PathVariable Long userId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(inviter -> {
                    try {
                        String message = body != null ? body.get("message") : null;
                        ChannelInvitationDTO invitation = invitationService.createInvitation(
                                channelId, inviter.getId(), userId, message);
                        return ResponseEntity.ok(invitation);
                    } catch (IllegalArgumentException e) {
                        log.warn("Failed to create invitation: {}", e.getMessage());
                        return ResponseEntity.badRequest().<ChannelInvitationDTO>build();
                    }
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Accept an invitation
     */
    @PostMapping("/{id}/accept")
    public ResponseEntity<ChannelInvitationDTO> acceptInvitation(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    try {
                        ChannelInvitationDTO invitation = invitationService.acceptInvitation(id, user.getId());
                        return ResponseEntity.ok(invitation);
                    } catch (IllegalArgumentException e) {
                        log.warn("Failed to accept invitation {}: {}", id, e.getMessage());
                        return ResponseEntity.badRequest().<ChannelInvitationDTO>build();
                    }
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Decline an invitation
     */
    @PostMapping("/{id}/decline")
    public ResponseEntity<ChannelInvitationDTO> declineInvitation(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    try {
                        ChannelInvitationDTO invitation = invitationService.declineInvitation(id, user.getId());
                        return ResponseEntity.ok(invitation);
                    } catch (IllegalArgumentException e) {
                        log.warn("Failed to decline invitation {}: {}", id, e.getMessage());
                        return ResponseEntity.badRequest().<ChannelInvitationDTO>build();
                    }
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Cancel a sent invitation (by inviter)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelInvitation(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    try {
                        invitationService.cancelInvitation(id, user.getId());
                        return ResponseEntity.ok().<Void>build();
                    } catch (IllegalArgumentException e) {
                        log.warn("Failed to cancel invitation {}: {}", id, e.getMessage());
                        return ResponseEntity.badRequest().<Void>build();
                    }
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Get invitations for a channel (channel admin only)
     */
    @GetMapping("/channel/{channelId}")
    public ResponseEntity<List<ChannelInvitationDTO>> getChannelInvitations(
            @PathVariable Long channelId,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> ResponseEntity.ok(invitationService.getChannelInvitations(channelId)))
                .orElse(ResponseEntity.status(401).build());
    }
}
