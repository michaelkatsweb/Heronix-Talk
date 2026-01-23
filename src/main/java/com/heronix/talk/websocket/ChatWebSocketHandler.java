package com.heronix.talk.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.heronix.talk.model.domain.Channel;
import com.heronix.talk.model.domain.Message;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.*;
import com.heronix.talk.model.dto.EmergencyAlertDTO;
import com.heronix.talk.model.dto.NewsItemDTO;
import com.heronix.talk.model.enums.UserStatus;
import com.heronix.talk.service.*;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket handler for real-time chat messaging.
 *
 * Optimized for 200+ concurrent users with:
 * - Async broadcasting using thread pool
 * - Channel member caching to reduce DB queries
 * - Rate limiting to prevent abuse
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

    // Thread pool for async broadcasting (sized for 200+ users)
    private final ExecutorService broadcastExecutor = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors()),
            r -> {
                Thread t = new Thread(r, "ws-broadcast");
                t.setDaemon(true);
                return t;
            }
    );

    // Cache for channel member IDs (reduces DB queries from ~1000/sec to ~50/sec)
    private final Cache<Long, List<Long>> channelMemberCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    // Rate limiting: track message counts per user (messages per minute)
    private final Map<Long, AtomicInteger> userMessageCounts = new ConcurrentHashMap<>();
    private static final int MAX_MESSAGES_PER_MINUTE = 60;

    // Scheduled executor for rate limit cleanup
    private final ScheduledExecutorService rateLimitCleanup = Executors.newSingleThreadScheduledExecutor();

    {
        // Reset rate limits every minute
        rateLimitCleanup.scheduleAtFixedRate(
                () -> userMessageCounts.clear(),
                1, 1, TimeUnit.MINUTES
        );
    }

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
            // Rate limiting check
            if (!checkRateLimit(user.getId())) {
                sendError(session, "Rate limit exceeded. Please slow down.");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) wsMessage.getPayload();

            Long channelId = Long.valueOf(payload.get("channelId").toString());
            String content = (String) payload.get("content");
            String clientId = (String) payload.get("clientId");

            log.info("Processing chat message: channelId={}, user={}, clientId={}, contentLength={}",
                    channelId, user.getUsername(), clientId, content != null ? content.length() : 0);

            // Verify user is member of channel
            if (!channelService.isMember(channelId, user.getId())) {
                log.warn("User {} is not a member of channel {}", user.getUsername(), channelId);
                sendError(session, "Not a member of this channel");
                return;
            }

            Channel channel = channelService.findById(channelId).orElse(null);
            if (channel == null) {
                log.warn("Channel {} not found", channelId);
                sendError(session, "Channel not found");
                return;
            }

            SendMessageRequest request = SendMessageRequest.builder()
                    .channelId(channelId)
                    .content(content)
                    .clientId(clientId)
                    .build();

            Object replyToId = payload.get("replyToId");
            if (replyToId != null) {
                request.setReplyToId(Long.valueOf(replyToId.toString()));
            }

            Message message = messageService.sendMessage(request, user, channel);
            if (message != null) {
                MessageDTO messageDTO = MessageDTO.fromEntity(message);

                log.info("Message saved successfully: id={}, uuid={}, broadcasting to channel {}",
                        message.getId(), message.getMessageUuid(), channelId);

                // Broadcast to channel members
                broadcastToChannel(channelId, ChatWsMessage.success(
                        ChatWsMessage.TYPE_MESSAGE,
                        ChatWsMessage.ACTION_CREATE,
                        messageDTO
                ));

                // Clear typing indicator
                presenceService.setTyping(channelId, user.getId(), false);
            } else {
                // Message was not saved - likely duplicate clientId
                log.warn("Message not saved for channel {}, clientId={} - possible duplicate",
                        channelId, clientId);
                sendError(session, "Message not saved - duplicate message detected");
            }

        } catch (Exception e) {
            log.error("Error handling chat message", e);
            sendError(session, "Error sending message: " + e.getMessage());
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

    /**
     * Broadcast message to all channel members asynchronously.
     * Uses cached member list and thread pool for efficient delivery.
     */
    public void broadcastToChannel(Long channelId, ChatWsMessage<?> message) {
        message.setChannelId(channelId);

        // Get member IDs from cache or database
        List<Long> memberIds = getChannelMemberIdsCached(channelId);

        log.info("Broadcasting to channel {}: {} members, {} online sessions",
                channelId, memberIds.size(), userSessions.size());

        // Pre-serialize the message once (not per-recipient)
        final String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Failed to serialize message for broadcast", e);
            return;
        }

        // Submit async broadcast task
        broadcastExecutor.submit(() -> {
            int sentCount = 0;
            for (Long memberId : memberIds) {
                String sessionId = userSessions.get(memberId);
                if (sessionId != null) {
                    WebSocketSession session = sessions.get(sessionId);
                    if (session != null && session.isOpen()) {
                        sendPreSerializedMessage(session, jsonMessage);
                        sentCount++;
                    }
                }
            }
            log.debug("Async broadcast complete: sent to {} of {} members", sentCount, memberIds.size());
        });
    }

    /**
     * Broadcast message to channel members except specified user (async).
     */
    public void broadcastToChannelExcept(Long channelId, Long excludeUserId, ChatWsMessage<?> message) {
        message.setChannelId(channelId);

        List<Long> memberIds = getChannelMemberIdsCached(channelId);

        // Pre-serialize once
        final String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Failed to serialize message for broadcast", e);
            return;
        }

        broadcastExecutor.submit(() -> {
            for (Long memberId : memberIds) {
                if (!memberId.equals(excludeUserId)) {
                    String sessionId = userSessions.get(memberId);
                    if (sessionId != null) {
                        WebSocketSession session = sessions.get(sessionId);
                        if (session != null && session.isOpen()) {
                            sendPreSerializedMessage(session, jsonMessage);
                        }
                    }
                }
            }
        });
    }

    /**
     * Get channel member IDs with caching.
     * Cache reduces DB queries from ~1000/sec to ~50/sec under heavy load.
     */
    private List<Long> getChannelMemberIdsCached(Long channelId) {
        return channelMemberCache.get(channelId, id -> channelService.getChannelMemberIds(id));
    }

    /**
     * Invalidate channel member cache when membership changes.
     */
    public void invalidateChannelMemberCache(Long channelId) {
        channelMemberCache.invalidate(channelId);
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

    /**
     * Broadcast news item to all connected clients.
     * Used when a news item is created or updated.
     */
    public void broadcastNews(NewsItemDTO newsItem, String action) {
        ChatWsMessage<NewsItemDTO> message = ChatWsMessage.success(
                ChatWsMessage.TYPE_NEWS,
                action,
                newsItem
        );

        log.info("Broadcasting news to {} connected clients: {}", sessions.size(), newsItem.getHeadline());

        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                sendMessage(session, message);
            }
        });
    }

    /**
     * Broadcast emergency alert to ALL connected clients.
     * Used for campus-wide emergency notifications.
     * This is the highest priority broadcast - reaches teachers, staff, and all users.
     */
    public void broadcastAlert(EmergencyAlertDTO alert, String action) {
        ChatWsMessage<EmergencyAlertDTO> message = ChatWsMessage.success(
                ChatWsMessage.TYPE_ALERT,
                action,
                alert
        );

        log.warn("BROADCASTING ALERT to {} connected clients: [{}] {}",
                sessions.size(), alert.getAlertLevel(), alert.getTitle());

        // Pre-serialize for efficiency when broadcasting to many clients
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);

            // Use async broadcast for high-priority delivery
            sessions.values().forEach(session -> {
                if (session.isOpen()) {
                    broadcastExecutor.submit(() -> sendPreSerializedMessage(session, jsonMessage));
                }
            });
        } catch (Exception e) {
            log.error("Error serializing alert for broadcast", e);
            // Fallback to synchronous send
            sessions.values().forEach(session -> {
                if (session.isOpen()) {
                    sendMessage(session, message);
                }
            });
        }
    }

    /**
     * Broadcast notification to all connected clients.
     */
    public void broadcastNotification(Object notification, String action) {
        ChatWsMessage<Object> message = ChatWsMessage.success(
                ChatWsMessage.TYPE_NOTIFICATION,
                action,
                notification
        );

        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                sendMessage(session, message);
            }
        });
    }

    /**
     * Send notification to a specific user.
     */
    public void sendNotificationToUser(Long userId, Object notification, String action) {
        ChatWsMessage<Object> message = ChatWsMessage.success(
                ChatWsMessage.TYPE_NOTIFICATION,
                action,
                notification
        );
        sendToUser(userId, message);
    }

    private void sendMessage(WebSocketSession session, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.error("Error sending WebSocket message", e);
        }
    }

    /**
     * Send pre-serialized JSON message (more efficient for broadcasts).
     */
    private void sendPreSerializedMessage(WebSocketSession session, String jsonMessage) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(jsonMessage));
                }
            }
        } catch (IOException e) {
            log.debug("Error sending WebSocket message to session {}", session.getId());
        }
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        ChatWsMessage<Void> error = ChatWsMessage.error(ChatWsMessage.TYPE_ERROR, errorMessage);
        sendMessage(session, error);
    }

    /**
     * Check if user is within rate limit.
     * Returns true if message is allowed, false if rate limited.
     */
    private boolean checkRateLimit(Long userId) {
        AtomicInteger count = userMessageCounts.computeIfAbsent(userId, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();

        if (currentCount > MAX_MESSAGES_PER_MINUTE) {
            log.warn("User {} exceeded rate limit ({} messages/minute)", userId, MAX_MESSAGES_PER_MINUTE);
            return false;
        }
        return true;
    }

    public int getActiveConnectionCount() {
        return sessions.size();
    }

    public boolean isUserOnline(Long userId) {
        return userSessions.containsKey(userId);
    }

    /**
     * Cleanup resources on shutdown.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down WebSocket handler...");
        broadcastExecutor.shutdown();
        rateLimitCleanup.shutdown();
        try {
            if (!broadcastExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                broadcastExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            broadcastExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("WebSocket handler shutdown complete");
    }
}
