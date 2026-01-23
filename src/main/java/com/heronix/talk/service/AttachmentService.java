package com.heronix.talk.service;

import com.heronix.talk.model.domain.Attachment;
import com.heronix.talk.model.domain.Message;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.enums.MessageType;
import com.heronix.talk.model.enums.SyncStatus;
import com.heronix.talk.repository.AttachmentRepository;
import com.heronix.talk.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for handling file attachments
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final MessageRepository messageRepository;
    private final FileValidationService fileValidationService;

    @Value("${heronix.talk.storage.path:./uploads}")
    private String storagePath;

    @Value("${heronix.talk.storage.max-file-size:10485760}")
    private long maxFileSize; // 10MB default

    /**
     * Upload a file attachment for a message
     */
    @Transactional
    public Attachment uploadAttachment(MultipartFile file, Long messageId, User uploader) throws IOException {
        // Comprehensive file validation (size, type, magic bytes, security checks)
        FileValidationService.ValidationResult validation = fileValidationService.validateFile(file);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }

        // Get the message
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        // Generate storage path
        String uuid = UUID.randomUUID().toString();
        String originalFileName = file.getOriginalFilename();
        String extension = getFileExtension(originalFileName);
        String storedFileName = uuid + (extension.isEmpty() ? "" : "." + extension);

        // Create directory structure: uploads/{year}/{month}/{day}/
        LocalDateTime now = LocalDateTime.now();
        String relativePath = String.format("%d/%02d/%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        Path uploadDir = Paths.get(storagePath, relativePath);
        Files.createDirectories(uploadDir);

        // Save file
        Path filePath = uploadDir.resolve(storedFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Calculate checksum
        String checksum = calculateMD5(file.getBytes());

        // Create attachment record
        Attachment attachment = Attachment.builder()
                .attachmentUuid(uuid)
                .message(message)
                .uploader(uploader)
                .originalFileName(originalFileName)
                .storagePath(relativePath + "/" + storedFileName)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .checksum(checksum)
                .syncStatus(SyncStatus.SYNCED)
                .build();

        // Update message with attachment info
        message.setAttachmentPath(attachment.getStoragePath());
        message.setAttachmentName(originalFileName);
        message.setAttachmentType(file.getContentType());
        message.setAttachmentSize(file.getSize());

        // Set message type based on content
        if (file.getContentType() != null && file.getContentType().startsWith("image/")) {
            message.setMessageType(MessageType.IMAGE);
        } else {
            message.setMessageType(MessageType.FILE);
        }
        messageRepository.save(message);

        Attachment saved = attachmentRepository.save(attachment);
        log.info("Uploaded attachment {} for message {}", uuid, messageId);
        return saved;
    }

    /**
     * Upload a file and create a new message with it
     */
    @Transactional
    public Message uploadFileWithMessage(MultipartFile file, Long channelId, User sender, String caption) throws IOException {
        // Comprehensive file validation (size, type, magic bytes, security checks)
        FileValidationService.ValidationResult validation = fileValidationService.validateFile(file);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }

        // Generate storage path
        String uuid = UUID.randomUUID().toString();
        String originalFileName = file.getOriginalFilename();
        String extension = getFileExtension(originalFileName);
        String storedFileName = uuid + (extension.isEmpty() ? "" : "." + extension);

        // Create directory structure
        LocalDateTime now = LocalDateTime.now();
        String relativePath = String.format("%d/%02d/%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        Path uploadDir = Paths.get(storagePath, relativePath);
        Files.createDirectories(uploadDir);

        // Save file
        Path filePath = uploadDir.resolve(storedFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Calculate checksum
        String checksum = calculateMD5(file.getBytes());

        // Determine message type
        MessageType messageType = MessageType.FILE;
        if (file.getContentType() != null && file.getContentType().startsWith("image/")) {
            messageType = MessageType.IMAGE;
        }

        // Create message with attachment
        Message message = Message.builder()
                .channel(messageRepository.findById(channelId)
                        .map(Message::getChannel)
                        .orElseThrow(() -> new IllegalArgumentException("Channel not found")))
                .sender(sender)
                .content(caption != null && !caption.isEmpty() ? caption : "[File: " + originalFileName + "]")
                .messageType(messageType)
                .attachmentPath(relativePath + "/" + storedFileName)
                .attachmentName(originalFileName)
                .attachmentType(file.getContentType())
                .attachmentSize(file.getSize())
                .build();

        // Need to get channel from ChannelRepository instead
        // For now, let's assume we have the channel reference
        message = messageRepository.save(message);

        // Create attachment record
        Attachment attachment = Attachment.builder()
                .attachmentUuid(uuid)
                .message(message)
                .uploader(sender)
                .originalFileName(originalFileName)
                .storagePath(relativePath + "/" + storedFileName)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .checksum(checksum)
                .syncStatus(SyncStatus.SYNCED)
                .build();

        attachmentRepository.save(attachment);
        log.info("Created message {} with attachment {} in channel", message.getId(), uuid);
        return message;
    }

    /**
     * Get attachment by UUID
     */
    public Optional<Attachment> getByUuid(String uuid) {
        return attachmentRepository.findByAttachmentUuid(uuid);
    }

    /**
     * Get attachments for a message
     */
    public List<Attachment> getByMessageId(Long messageId) {
        return attachmentRepository.findByMessageId(messageId);
    }

    /**
     * Get file path for download
     */
    public Path getFilePath(String relativePath) {
        return Paths.get(storagePath, relativePath);
    }

    /**
     * Delete attachment (soft delete)
     */
    @Transactional
    public void deleteAttachment(Long attachmentId) {
        attachmentRepository.findById(attachmentId).ifPresent(attachment -> {
            attachment.setDeleted(true);
            attachmentRepository.save(attachment);
            log.info("Soft deleted attachment {}", attachmentId);
        });
    }

    /**
     * Get total storage used by a user
     */
    public long getTotalStorageUsed(Long userId) {
        Long total = attachmentRepository.getTotalFileSizeByUploader(userId);
        return total != null ? total : 0L;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    private String calculateMD5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Could not calculate MD5 checksum", e);
            return null;
        }
    }
}
