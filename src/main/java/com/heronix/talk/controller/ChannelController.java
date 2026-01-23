package com.heronix.talk.controller;

import com.heronix.talk.model.domain.Channel;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.ChannelDTO;
import com.heronix.talk.model.dto.ChannelPreferencesDTO;
import com.heronix.talk.model.dto.CreateChannelRequest;
import com.heronix.talk.service.AuthenticationService;
import com.heronix.talk.service.ChannelService;
import com.heronix.talk.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for channel operations.
 */
@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
@Slf4j
public class ChannelController {

    private final ChannelService channelService;
    private final AuthenticationService authenticationService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<ChannelDTO>> getAllChannels() {
        return ResponseEntity.ok(channelService.getAllActiveChannels());
    }

    @GetMapping("/public")
    public ResponseEntity<List<ChannelDTO>> getPublicChannels() {
        return ResponseEntity.ok(channelService.getPublicChannels());
    }

    @GetMapping("/announcements")
    public ResponseEntity<List<ChannelDTO>> getAnnouncementChannels() {
        return ResponseEntity.ok(channelService.getAnnouncementChannels());
    }

    @GetMapping("/my")
    public ResponseEntity<List<ChannelDTO>> getMyChannels(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> ResponseEntity.ok(channelService.getUserChannels(user.getId())))
                .orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/dm")
    public ResponseEntity<List<ChannelDTO>> getMyDirectMessages(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> ResponseEntity.ok(channelService.getUserDirectMessages(user.getId())))
                .orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChannelDTO> getChannel(@PathVariable Long id) {
        return channelService.findById(id)
                .map(channel -> ResponseEntity.ok(ChannelDTO.fromEntity(channel)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<List<ChannelDTO>> searchChannels(@RequestParam String q) {
        return ResponseEntity.ok(channelService.searchChannels(q));
    }

    @PostMapping
    public ResponseEntity<ChannelDTO> createChannel(
            @RequestBody CreateChannelRequest request,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    var channel = channelService.createChannel(request, user);
                    return ResponseEntity.ok(ChannelDTO.fromEntity(channel));
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/dm/{targetUserId}")
    public ResponseEntity<ChannelDTO> getOrCreateDirectMessage(
            @PathVariable Long targetUserId,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .flatMap(user -> userService.findById(targetUserId)
                        .map(targetUser -> {
                            Channel dmChannel = channelService.getOrCreateDirectMessage(user, targetUser);
                            return ResponseEntity.ok(ChannelDTO.fromEntity(dmChannel));
                        }))
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<Void> joinChannel(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .flatMap(user -> channelService.findById(id).map(channel -> {
                    channelService.addMember(channel, user, false, false);
                    return ResponseEntity.ok().<Void>build();
                }))
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leaveChannel(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    channelService.removeMember(id, user.getId());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChannel(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        // Would need permission check
        channelService.deleteChannel(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Update channel-specific preferences for the current user.
     */
    @PutMapping("/{id}/preferences")
    public ResponseEntity<Void> updateChannelPreferences(
            @PathVariable Long id,
            @RequestBody ChannelPreferencesDTO preferences,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    channelService.updateChannelPreferences(
                            id,
                            user.getId(),
                            preferences.getMuted(),
                            preferences.getPinned(),
                            preferences.getFavorite(),
                            preferences.getNotifyOnMessage(),
                            preferences.getNotifyOnMention()
                    );
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Toggle mute status for a channel.
     */
    @PostMapping("/{id}/mute")
    public ResponseEntity<Void> toggleMute(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    channelService.toggleMute(id, user.getId());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Toggle favorite status for a channel.
     */
    @PostMapping("/{id}/favorite")
    public ResponseEntity<Void> toggleFavorite(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    channelService.toggleFavorite(id, user.getId());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Toggle pin status for a channel.
     */
    @PostMapping("/{id}/pin")
    public ResponseEntity<Void> togglePin(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    channelService.togglePin(id, user.getId());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.status(401).build());
    }
}
