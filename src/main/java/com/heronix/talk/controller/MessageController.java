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
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(messageService.getChannelMessages(channelId, page, size));
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
    public ResponseEntity<Void> addReaction(
            @PathVariable Long id,
            @RequestParam String emoji,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    messageService.addReaction(id, emoji, user.getId());
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.status(401).build());
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
}
