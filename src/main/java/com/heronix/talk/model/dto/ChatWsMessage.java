package com.heronix.talk.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Generic WebSocket message wrapper for real-time communication.
 * Used for all WebSocket messages between server and clients.
 *
 * Named ChatWsMessage to avoid collision with Spring's WebSocketMessage interface.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatWsMessage<T> {

    private String type;
    private String action;
    private T payload;
    private Long userId;
    private Long channelId;
    private String correlationId;
    private boolean success;
    private String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    // Message types
    public static final String TYPE_MESSAGE = "MESSAGE";
    public static final String TYPE_CHANNEL = "CHANNEL";
    public static final String TYPE_USER = "USER";
    public static final String TYPE_PRESENCE = "PRESENCE";
    public static final String TYPE_TYPING = "TYPING";
    public static final String TYPE_NOTIFICATION = "NOTIFICATION";
    public static final String TYPE_NEWS = "NEWS";
    public static final String TYPE_ALERT = "ALERT";
    public static final String TYPE_ERROR = "ERROR";
    public static final String TYPE_ACK = "ACK";
    public static final String TYPE_SYSTEM = "SYSTEM";

    // Actions
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_JOIN = "JOIN";
    public static final String ACTION_LEAVE = "LEAVE";
    public static final String ACTION_READ = "READ";
    public static final String ACTION_TYPING_START = "TYPING_START";
    public static final String ACTION_TYPING_STOP = "TYPING_STOP";
    public static final String ACTION_ONLINE = "ONLINE";
    public static final String ACTION_OFFLINE = "OFFLINE";
    public static final String ACTION_STATUS_CHANGE = "STATUS_CHANGE";
    public static final String ACTION_REACTION = "REACTION";
    public static final String ACTION_PIN = "PIN";
    public static final String ACTION_UNPIN = "UNPIN";

    public static <T> ChatWsMessage<T> success(String type, String action, T payload) {
        return ChatWsMessage.<T>builder()
                .type(type)
                .action(action)
                .payload(payload)
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ChatWsMessage<T> error(String type, String errorMessage) {
        return ChatWsMessage.<T>builder()
                .type(TYPE_ERROR)
                .success(false)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ChatWsMessage<Void> ack(String correlationId) {
        return ChatWsMessage.<Void>builder()
                .type(TYPE_ACK)
                .correlationId(correlationId)
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
