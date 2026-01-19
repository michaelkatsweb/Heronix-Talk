package com.heronix.talk.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heronix.talk.model.domain.Channel;
import com.heronix.talk.model.domain.Message;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.*;
import com.heronix.talk.model.enums.UserStatus;
import com.heronix.talk.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time chat messaging.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final AuthenticationService authenticationService;
    private final MessageService messageService;
    private final ChannelService channelService;
    private final UserService userService;
    private final PresenceService presenceService;

    // Active WebSocket sessions: sessionId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // User to session mapping: userId -> sessionId
    private final Map<Long, String> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        String sessionToken = (String) session.getAttributes().get("sessionToken");

        sessions.put(session.getId(), session);
        userSessions.put(userId, session.getId());

        // Update auth service with websocket session
        authenticationService.updateWebsocketSession(sessionToken, session.getId());

        // Set user online
        PresenceUpdate presence = presenceService.setUserOnline(userId);
        if (presence != null) {
            broadcastPresenceUpdate(presence);
        }

        log.info("WebSocket connection established: user={}, sessionId={}", userId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");

        sessions.remove(session.getId());
        userSessions.remove(userId);

        // Handle disconnect
        authenticationService.handleWebsocketDisconnect(session.getId());

        // Check if user has other active sessions
        String otherSession = userSessions.get(userId);
        if (otherSession == null) {
            PresenceUpdate presence = presenceService.setUserOffline(userId);
            if (presence != null) {
                broadcastPresenceUpdate(presence);
            }
        }

        log.info("WebSocket connection closed: user={}, sessionId={}, status={}",
                userId, session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        try {
            Long userId = (Long) session.getAttributes().get("userId");
            User user = (User) session.getAttributes().get("user");

            ChatWsMessage<?> wsMessage = objectMapper.readValue(
                    textMessage.getPayload(), ChatWsMessage.class);

            log.debug("Received WebSocket message: type={}, action={}, user={}",
                    wsMessage.getType(), wsMessage.getAction(), userId);

            switch (wsMessage.getType()) {
                case ChatWsMessage.TYPE_MESSAGE -> handleChatMessage(wsMessage, user, session);
                case ChatWsMessage.TYPE_TYPING -> handleTypingIndicator(wsMessage, user);
                case ChatWsMessage.TYPE_PRESENCE -> handlePresenceUpdate(wsMessage, user);
                case ChatWsMessage.TYPE_CHANNEL -> handleChannelAction(wsMessage, user, session);
                default -> log.warn("Unknown message type: {}", wsMessage.getType());
            }

            // Update user activity
            presenceService.recordHeartbeat(userId);

        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
            sendError(session, "Error processing message: " + e.getMessage());
        }
    }

    private void handleChatMessage(ChatWsMessage<?> wsMessage, User user, WebSocketSession session) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) wsMessage.getPayload();

            Long channelId = Long.valueOf(payload.get("channelId").toString());
            String content = (String) payload.get("content");
            String clientId = (String) payload.get("clientId");

            // Verify user is member of channel
            if (!channelService.isMember(channelId, user.getId())) {
                sendError(session, "Not a member of this channel");
                return;
            }

            Channel channel = channelService.findById(channelId).orElse(null);
            if (channel == null) {
                sendError(session, "Channel not found");
                return;
            }

            SendMessageRequest request = SendMessageRequest.builder()
                    .channelId(channelId)
                    .content(content)
                    .clientId(clientId)
                    .build();

            if (payload.containsKey("replyToId")) {
                request.setReplyToId(Long.valueOf(payload.get("replyToId").toString()));
            }

            Message message = messageService.sendMessage(request, user, channel);
            if (message != null) {
                MessageDTO messageDTO = MessageDTO.fromEntity(message);

                // Broadcast to channel members
                broadcastToChannel(channelId, ChatWsMessage.success(
                        ChatWsMessage.TYPE_MESSAGE,
                        ChatWsMessage.ACTION_CREATE,
                        messageDTO
                ));

                // Clear typing indicator
                presenceService.setTyping(channelId, user.getId(), false);
            }

        } catch (Exception e) {
            log.error("Error handling chat message", e);
            sendError(session, "Error sending message");
        }
    }

    private void handleTypingIndicator(ChatWsMessage<?> wsMessage, User user) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) wsMessage.getPayload();

            Long channelId = Long.valueOf(payload.get("channelId").toString());
            boolean isTyping = (Boolean) payload.getOrDefault("isTyping", true);

            presenceService.setTyping(channelId, user.getId(), isTyping);

            TypingIndicator indicator = isTyping
                    ? TypingIndicator.start(channelId, user.getId(), user.getFullName())
                    : TypingIndicator.stop(channelId, user.getId(), user.getFullName());

            // Broadcast to channel members (except sender)
            broadcastToChannelExcept(channelId, user.getId(), ChatWsMessage.success(
                    ChatWsMessage.TYPE_TYPING,
                    isTyping ? ChatWsMessage.ACTION_TYPING_START : ChatWsMessage.ACTION_TYPING_STOP,
                    indicator
            ));

        } catch (Exception e) {
            log.error("Error handling typing indicator", e);
        }
    }

    private void handlePresenceUpdate(ChatWsMessage<?> wsMessage, User user) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) wsMessage.getPayload();

            String statusStr = (String) payload.get("status");
            String statusMessage = (String) payload.get("statusMessage");

            UserStatus status = UserStatus.valueOf(statusStr);
            PresenceUpdate presence = presenceService.updateStatus(user.getId(), status, statusMessage);

            if (presence != null) {
                broadcastPresenceUpdate(presence);
            }

        } catch (Exception e) {
            log.error("Error handling presence update", e);
        }
    }

    private void handleChannelAction(ChatWsMessage<?> wsMessage, User user, WebSocketSession session) {
        try {
            String action = wsMessage.getAction();

            switch (action) {
                case ChatWsMessage.ACTION_JOIN -> {
                    Long channelId = wsMessage.getChannelId();
                    // Subscribe to channel updates
                    sendChannelHistory(session, channelId);
                }
                case ChatWsMessage.ACTION_READ -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = (Map<String, Object>) wsMessage.getPayload();
                    Long channelId = Long.valueOf(payload.get("channelId").toString());
                    Long messageId = Long.valueOf(payload.get("messageId").toString());
                    messageService.markAsRead(channelId, user.getId(), messageId);
                }
            }

        } catch (Exception e) {
            log.error("Error handling channel action", e);
        }
    }

    private void sendChannelHistory(WebSocketSession session, Long channelId) {
        try {
            List<MessageDTO> messages = messageService.getChannelMessages(channelId, 0, 50);

            ChatWsMessage<List<MessageDTO>> historyMessage = ChatWsMessage.success(
                    ChatWsMessage.TYPE_MESSAGE,
                    "HISTORY",
                    messages
            );
            historyMessage.setChannelId(channelId);

            sendMessage(session, historyMessage);

        } catch (Exception e) {
            log.error("Error sending channel history", e);
        }
    }

    public void broadcastToChannel(Long channelId, ChatWsMessage<?> message) {
        message.setChannelId(channelId);
        List<Long> memberIds = channelService.getChannelMemberIds(channelId);

        for (Long memberId : memberIds) {
            String sessionId = userSessions.get(memberId);
            if (sessionId != null) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    sendMessage(session, message);
                }
            }
        }
    }

    public void broadcastToChannelExcept(Long channelId, Long excludeUserId, ChatWsMessage<?> message) {
        message.setChannelId(channelId);
        List<Long> memberIds = channelService.getChannelMemberIds(channelId);

        for (Long memberId : memberIds) {
            if (!memberId.equals(excludeUserId)) {
                String sessionId = userSessions.get(memberId);
                if (sessionId != null) {
                    WebSocketSession session = sessions.get(sessionId);
                    if (session != null && session.isOpen()) {
                        sendMessage(session, message);
                    }
                }
            }
        }
    }

    public void broadcastPresenceUpdate(PresenceUpdate presence) {
        ChatWsMessage<PresenceUpdate> message = ChatWsMessage.success(
                ChatWsMessage.TYPE_PRESENCE,
                ChatWsMessage.ACTION_STATUS_CHANGE,
                presence
        );

        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                sendMessage(session, message);
            }
        });
    }

    public void sendToUser(Long userId, ChatWsMessage<?> message) {
        String sessionId = userSessions.get(userId);
        if (sessionId != null) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                sendMessage(session, message);
            }
        }
    }

    private void sendMessage(WebSocketSession session, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("Error sending WebSocket message", e);
        }
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        ChatWsMessage<Void> error = ChatWsMessage.error(ChatWsMessage.TYPE_ERROR, errorMessage);
        sendMessage(session, error);
    }

    public int getActiveConnectionCount() {
        return sessions.size();
    }

    public boolean isUserOnline(Long userId) {
        return userSessions.containsKey(userId);
    }
}
