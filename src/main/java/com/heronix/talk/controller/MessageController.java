package com.heronix.talk.controller;

import com.heronix.talk.model.dto.MessageDTO;
import com.heronix.talk.model.dto.SendMessageRequest;
import com.heronix.talk.service.AuthenticationService;
import com.heronix.talk.service.ChannelService;
import com.heronix.talk.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for message operations.
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final MessageService messageService;
    private final ChannelService channelService;
    private final AuthenticationService authenticationService;

    @GetMapping("/channel/{channelId}")
    public ResponseEntity<List<MessageDTO>> getChannelMessages(
            @PathVariable Long channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {

        // Validate channel exists
        var channelOpt = channelService.findById(channelId);
        if (channelOpt.isEmpty()) {
            log.warn("Channel {} not found", channelId);
            return ResponseEntity.notFound().build();
        }

        var channel = channelOpt.get();

        // For public channels, allow access without authentication
        if ("PUBLIC".equals(channel.getChannelType())) {
            return ResponseEntity.ok(messageService.getChannelMessages(channelId, page, size));
        }

        // For non-public channels, require authentication and membership
        if (sessionToken == null || sessionToken.isEmpty()) {
            log.warn("No session token provided for private channel {}", channelId);
            return ResponseEntity.status(401).build();
        }

        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    if (channelService.isMember(channelId, user.getId())) {
                        return ResponseEntity.ok(messageService.getChannelMessages(channelId, page, size));
                    } else {
                        log.warn("User {} is not a member of channel {}", user.getId(), channelId);
                        return ResponseEntity.status(403).<List<MessageDTO>>build();
                    }
                })
                .orElseGet(() -> {
                    log.warn("Invalid session token for channel {} access", channelId);
                    return ResponseEntity.status(401).build();
                });
    }

    @GetMapping("/channel/{channelId}/pinned")
    public ResponseEntity<List<MessageDTO>> getPinnedMessages(@PathVariable Long channelId) {
        return ResponseEntity.ok(messageService.getPinnedMessages(channelId));
    }

    @GetMapping("/{id}/replies")
    public ResponseEntity<List<MessageDTO>> getReplies(@PathVariable Long id) {
        return ResponseEntity.ok(messageService.getReplies(id));
    }

    @GetMapping("/search")
    public ResponseEntity<List<MessageDTO>> searchMessages(
            @RequestParam String q,
            @RequestParam(required = false) Long channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (channelId != null) {
            return ResponseEntity.ok(messageService.searchInChannel(channelId, q, page, size));
        }
        return ResponseEntity.ok(messageService.searchAll(q, page, size));
    }

    @PostMapping
    public ResponseEntity<MessageDTO> sendMessage(
            @RequestBody SendMessageRequest request,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .flatMap(user -> channelService.findById(request.getChannelId())
                        .filter(channel -> channelService.isMember(channel.getId(), user.getId()))
                        .map(channel -> {
                            var message = messageService.sendMessage(request, user, channel);
                            return message != null
                                    ? ResponseEntity.ok(MessageDTO.fromEntity(message))
                                    : ResponseEntity.badRequest().<MessageDTO>build();
                        }))
                .orElse(ResponseEntity.status(401).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<MessageDTO> editMessage(
            @PathVariable Long id,
            @RequestBody String content,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    var message = messageService.editMessage(id, content, user.getId());
                    return message != null
                            ? ResponseEntity.ok(MessageDTO.fromEntity(message))
                            : ResponseEntity.notFound().<MessageDTO>build();
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    boolean deleted = messageService.deleteMessage(id, user.getId());
                    return deleted
                            ? ResponseEntity.ok().<Void>build()
                            : ResponseEntity.notFound().<Void>build();
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/{id}/pin")
    public ResponseEntity<Void> pinMessage(
            @PathVariable Long id,
            @RequestParam boolean pinned) {
        messageService.pinMessage(id, pinned);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reaction")
    public ResponseEntity<Map<String, List<Long>>> addReaction(
            @PathVariable Long id,
            @RequestParam String emoji,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    var reactions = messageService.addReaction(id, emoji, user.getId());
                    return reactions != null
                            ? ResponseEntity.ok(reactions)
                            : ResponseEntity.notFound().<Map<String, List<Long>>>build();
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @DeleteMapping("/{id}/reaction")
    public ResponseEntity<Map<String, List<Long>>> removeReaction(
            @PathVariable Long id,
            @RequestParam String emoji,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    var reactions = messageService.removeReaction(id, emoji, user.getId());
                    return reactions != null
                            ? ResponseEntity.ok(reactions)
                            : ResponseEntity.notFound().<Map<String, List<Long>>>build();
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/{id}/reaction/toggle")
    public ResponseEntity<Map<String, List<Long>>> toggleReaction(
            @PathVariable Long id,
            @RequestParam String emoji,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    var reactions = messageService.toggleReaction(id, emoji, user.getId());
                    return reactions != null
                            ? ResponseEntity.ok(reactions)
                            : ResponseEntity.notFound().<Map<String, List<Long>>>build();
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/{id}/reactions")
    public ResponseEntity<Map<String, List<Long>>> getReactions(@PathVariable Long id) {
        return ResponseEntity.ok(messageService.getReactions(id));
    }

    @PostMapping("/channel/{channelId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long channelId,
            @RequestParam Long messageId,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    messageService.markAsRead(channelId, user.getId(), messageId);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @GetMapping("/{id}/read-receipts")
    public ResponseEntity<Map<String, Object>> getReadReceipts(
            @PathVariable Long id,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
        var receipts = messageService.getReadReceipts(id);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("messageId", id);
        response.put("readCount", receipts.size());
        response.put("receipts", receipts);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/mentions")
    public ResponseEntity<List<Long>> getMentions(@PathVariable Long id) {
        return ResponseEntity.ok(messageService.getMentionedUserIds(id));
    }

    @GetMapping("/{id}/is-mentioned/{userId}")
    public ResponseEntity<Boolean> isMentioned(
            @PathVariable Long id,
            @PathVariable Long userId) {
        return ResponseEntity.ok(messageService.isMentioned(id, userId));
    }
}
