package com.heronix.talk.controller;

import com.heronix.talk.model.domain.Attachment;
import com.heronix.talk.model.domain.Channel;
import com.heronix.talk.model.domain.Message;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.MessageDTO;
import com.heronix.talk.model.enums.MessageType;
import com.heronix.talk.repository.ChannelRepository;
import com.heronix.talk.repository.MessageRepository;
import com.heronix.talk.service.AttachmentService;
import com.heronix.talk.service.AuthenticationService;
import com.heronix.talk.service.ChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for file attachment operations
 */
@Slf4j
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final AuthenticationService authenticationService;
    private final ChannelRepository channelRepository;
    private final MessageRepository messageRepository;
    private final ChannelService channelService;

    /**
     * Upload a file attachment for a channel
     * Creates a new message with the attachment
     */
    @PostMapping("/upload/channel/{channelId}")
    public ResponseEntity<?> uploadToChannel(
            @PathVariable Long channelId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestHeader("X-Session-Token") String sessionToken) {

        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    try {
                        // Verify user has access to channel
                        Channel channel = channelRepository.findById(channelId)
                                .orElse(null);
                        if (channel == null) {
                            return ResponseEntity.notFound().build();
                        }

                        if (!channelService.isMember(channelId, user.getId())) {
                            return ResponseEntity.status(403).body(Map.of("error", "Not a member of this channel"));
                        }

                        // Create message with attachment
                        Message message = createMessageWithAttachment(file, channel, user, caption);

                        log.info("User {} uploaded file {} to channel {}",
                                user.getUsername(), file.getOriginalFilename(), channelId);

                        return ResponseEntity.ok(MessageDTO.fromEntity(message));

                    } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
                    } catch (IOException e) {
                        log.error("Error uploading file", e);
                        return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload file"));
                    }
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Upload a file attachment for an existing message
     */
    @PostMapping("/upload/message/{messageId}")
    public ResponseEntity<?> uploadToMessage(
            @PathVariable Long messageId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-Session-Token") String sessionToken) {

        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    try {
                        Attachment attachment = attachmentService.uploadAttachment(file, messageId, user);

                        Map<String, Object> response = new HashMap<>();
                        response.put("attachmentId", attachment.getId());
                        response.put("uuid", attachment.getAttachmentUuid());
                        response.put("fileName", attachment.getOriginalFileName());
                        response.put("fileSize", attachment.getFileSize());
                        response.put("contentType", attachment.getContentType());

                        log.info("User {} attached file {} to message {}",
                                user.getUsername(), file.getOriginalFilename(), messageId);

                        return ResponseEntity.ok(response);

                    } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
                    } catch (IOException e) {
                        log.error("Error uploading file", e);
                        return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload file"));
                    }
                })
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Download an attachment by UUID
     */
    @GetMapping("/download/{uuid}")
    public ResponseEntity<?> downloadAttachment(
            @PathVariable String uuid,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {

        // Optional auth check - attachments may be public or require auth
        return attachmentService.getByUuid(uuid)
                .map(attachment -> {
                    try {
                        Path filePath = attachmentService.getFilePath(attachment.getStoragePath());
                        Resource resource = new UrlResource(filePath.toUri());

                        if (!resource.exists() || !resource.isReadable()) {
                            return ResponseEntity.notFound().<Resource>build();
                        }

                        String contentType = attachment.getContentType();
                        if (contentType == null) {
                            contentType = "application/octet-stream";
                        }

                        return ResponseEntity.ok()
                                .contentType(MediaType.parseMediaType(contentType))
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"" + attachment.getOriginalFileName() + "\"")
                                .body(resource);

                    } catch (MalformedURLException e) {
                        log.error("Error downloading file", e);
                        return ResponseEntity.internalServerError().<Resource>build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get attachment metadata
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<?> getAttachmentInfo(
            @PathVariable String uuid,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {

        return attachmentService.getByUuid(uuid)
                .map(attachment -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("id", attachment.getId());
                    info.put("uuid", attachment.getAttachmentUuid());
                    info.put("fileName", attachment.getOriginalFileName());
                    info.put("fileSize", attachment.getFileSize());
                    info.put("fileSizeFormatted", attachment.getFileSizeFormatted());
                    info.put("contentType", attachment.getContentType());
                    info.put("isImage", attachment.isImage());
                    info.put("isVideo", attachment.isVideo());
                    info.put("isAudio", attachment.isAudio());
                    info.put("isDocument", attachment.isDocument());
                    info.put("createdDate", attachment.getCreatedDate());

                    return ResponseEntity.ok(info);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete an attachment
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<?> deleteAttachment(
            @PathVariable String uuid,
            @RequestHeader("X-Session-Token") String sessionToken) {

        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> attachmentService.getByUuid(uuid)
                        .map(attachment -> {
                            // Only uploader can delete
                            if (!attachment.getUploader().getId().equals(user.getId())) {
                                return ResponseEntity.status(403).body(Map.of("error", "Not authorized to delete this attachment"));
                            }

                            attachmentService.deleteAttachment(attachment.getId());
                            return ResponseEntity.ok(Map.of("message", "Attachment deleted"));
                        })
                        .orElse(ResponseEntity.notFound().build()))
                .orElse(ResponseEntity.status(401).build());
    }

    /**
     * Get user's storage usage
     */
    @GetMapping("/storage/usage")
    public ResponseEntity<?> getStorageUsage(
            @RequestHeader("X-Session-Token") String sessionToken) {

        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    long used = attachmentService.getTotalStorageUsed(user.getId());
                    Map<String, Object> usage = new HashMap<>();
                    usage.put("usedBytes", used);
                    usage.put("usedFormatted", formatFileSize(used));
                    // Could add quota info here
                    return ResponseEntity.ok(usage);
                })
                .orElse(ResponseEntity.status(401).build());
    }

    private Message createMessageWithAttachment(MultipartFile file, Channel channel, User sender, String caption) throws IOException {
        // Determine message type
        MessageType messageType = MessageType.FILE;
        if (file.getContentType() != null && file.getContentType().startsWith("image/")) {
            messageType = MessageType.IMAGE;
        }

        String content = caption != null && !caption.isEmpty()
                ? caption
                : "[File: " + file.getOriginalFilename() + "]";

        // Create the message first
        Message message = Message.builder()
                .channel(channel)
                .sender(sender)
                .content(content)
                .messageType(messageType)
                .build();
        message = messageRepository.save(message);

        // Then upload attachment
        Attachment attachment = attachmentService.uploadAttachment(file, message.getId(), sender);

        // Reload message with attachment info
        return messageRepository.findById(message.getId()).orElse(message);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
