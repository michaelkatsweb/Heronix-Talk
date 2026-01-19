package com.heronix.talk.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO for typing indicator notifications.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingIndicator {
    private Long channelId;
    private Long userId;
    private String userName;
    private boolean isTyping;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    public static TypingIndicator start(Long channelId, Long userId, String userName) {
        return TypingIndicator.builder()
                .channelId(channelId)
                .userId(userId)
                .userName(userName)
                .isTyping(true)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static TypingIndicator stop(Long channelId, Long userId, String userName) {
        return TypingIndicator.builder()
                .channelId(channelId)
                .userId(userId)
                .userName(userName)
                .isTyping(false)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
