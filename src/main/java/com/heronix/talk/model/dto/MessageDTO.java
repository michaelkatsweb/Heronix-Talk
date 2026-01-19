package com.heronix.talk.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heronix.talk.model.domain.Message;
import com.heronix.talk.model.enums.MessageStatus;
import com.heronix.talk.model.enums.MessageType;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for Message entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {

    private Long id;
    private String messageUuid;
    private Long channelId;
    private String channelName;
    private Long senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private MessageType messageType;
    private MessageStatus status;
    private boolean edited;
    private boolean deleted;
    private boolean pinned;
    private boolean important;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime editedAt;

    // Reply information
    private Long replyToId;
    private String replyToPreview;
    private String replyToSenderName;
    private int replyCount;

    // Attachment information
    private String attachmentPath;
    private String attachmentName;
    private String attachmentType;
    private Long attachmentSize;

    // Reactions as JSON string
    private String reactions;

    // Mentions as JSON array of user IDs
    private String mentions;

    // Client tracking
    private String clientId;

    public static MessageDTO fromEntity(Message message) {
        if (message == null) return null;

        MessageDTO.MessageDTOBuilder builder = MessageDTO.builder()
                .id(message.getId())
                .messageUuid(message.getMessageUuid())
                .channelId(message.getChannel() != null ? message.getChannel().getId() : null)
                .channelName(message.getChannel() != null ? message.getChannel().getName() : null)
                .senderId(message.getSender() != null ? message.getSender().getId() : null)
                .senderName(message.getSender() != null ? message.getSender().getFullName() : null)
                .senderAvatar(message.getSender() != null ? message.getSender().getAvatarPath() : null)
                .content(message.getContent())
                .messageType(message.getMessageType())
                .status(message.getStatus())
                .edited(message.isEdited())
                .deleted(message.isDeleted())
                .pinned(message.isPinned())
                .important(message.isImportant())
                .timestamp(message.getTimestamp())
                .editedAt(message.getEditedAt())
                .replyCount(message.getReplyCount())
                .attachmentPath(message.getAttachmentPath())
                .attachmentName(message.getAttachmentName())
                .attachmentType(message.getAttachmentType())
                .attachmentSize(message.getAttachmentSize())
                .reactions(message.getReactions())
                .mentions(message.getMentions())
                .clientId(message.getClientId());

        if (message.getReplyTo() != null) {
            builder.replyToId(message.getReplyTo().getId())
                    .replyToPreview(message.getReplyTo().getPreview())
                    .replyToSenderName(message.getReplyTo().getSenderName());
        }

        return builder.build();
    }

    public boolean hasAttachment() {
        return attachmentPath != null && !attachmentPath.isEmpty();
    }

    public boolean isReply() {
        return replyToId != null;
    }
}
