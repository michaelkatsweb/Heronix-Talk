package com.heronix.talk.model.dto;

import com.heronix.talk.model.enums.MessageType;
import lombok.*;

import java.util.List;

/**
 * Request DTO for sending a new message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    private Long channelId;
    private String content;
    private MessageType messageType;
    private Long replyToId;
    private String clientId;
    private List<Long> mentionedUserIds;

    // For file attachments
    private String attachmentName;
    private String attachmentType;
    private Long attachmentSize;
}
