package com.heronix.talk.controller;

import com.heronix.talk.model.dto.PresenceUpdate;
import com.heronix.talk.model.enums.UserStatus;
import com.heronix.talk.service.AuthenticationService;
import com.heronix.talk.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for presence and status operations.
 */
@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
@Slf4j
public class PresenceController {

    private final PresenceService presenceService;
    private final AuthenticationService authenticationService;

    @GetMapping("/online")
    public ResponseEntity<List<PresenceUpdate>> getOnlineUsers() {
        return ResponseEntity.ok(presenceService.getOnlineUsers());
    }

    @GetMapping("/online/count")
    public ResponseEntity<Long> getOnlineUserCount() {
        return ResponseEntity.ok(presenceService.getOnlineUserCount());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<UserStatus> getUserStatus(@PathVariable Long userId) {
        return ResponseEntity.ok(presenceService.getUserStatus(userId));
    }

    @PostMapping("/status")
    public ResponseEntity<PresenceUpdate> updateStatus(
            @RequestBody Map<String, String> request,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    UserStatus status = UserStatus.valueOf(request.get("status"));
                    String statusMessage = request.get("statusMessage");
                    PresenceUpdate update = presenceService.updateStatus(user.getId(), status, statusMessage);
                    return update != null
                            ? ResponseEntity.ok(update)
                            : ResponseEntity.badRequest().<PresenceUpdate>build();
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@RequestHeader("X-Session-Token") String sessionToken) {
        authenticationService.getUserFromSession(sessionToken)
                .ifPresent(user -> presenceService.recordHeartbeat(user.getId()));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/typing/{channelId}")
    public ResponseEntity<List<Long>> getTypingUsers(@PathVariable Long channelId) {
        return ResponseEntity.ok(presenceService.getTypingUsers(channelId));
    }
}
