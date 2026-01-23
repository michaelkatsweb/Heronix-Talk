package com.heronix.talk.service;

import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.PresenceUpdate;
import com.heronix.talk.model.enums.UserStatus;
import com.heronix.talk.repository.UserRepository;
import com.heronix.talk.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing user presence and online status.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;

    // In-memory cache of typing indicators: channelId -> Map<userId, timestamp>
    private final Map<Long, Map<Long, LocalDateTime>> typingIndicators = new ConcurrentHashMap<>();

    // Track last heartbeat per user
    private final Map<Long, LocalDateTime> lastHeartbeat = new ConcurrentHashMap<>();

    public List<PresenceUpdate> getOnlineUsers() {
        return userRepository.findOnlineUsers().stream()
                .map(user -> PresenceUpdate.builder()
                        .userId(user.getId())
                        .userName(user.getFullName())
                        .status(user.getStatus())
                        .statusMessage(user.getStatusMessage())
                        .lastSeen(user.getLastSeen())
                        .timestamp(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());
    }

    public UserStatus getUserStatus(Long userId) {
        return userRepository.findById(userId)
                .map(User::getStatus)
                .orElse(UserStatus.OFFLINE);
    }

    @Transactional
    public PresenceUpdate setUserOnline(Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    user.setStatus(UserStatus.ONLINE);
                    user.setLastActivity(LocalDateTime.now());
                    userRepository.save(user);

                    lastHeartbeat.put(userId, LocalDateTime.now());

                    log.info("User {} is now online", user.getUsername());
                    return PresenceUpdate.online(userId, user.getFullName());
                })
                .orElse(null);
    }

    @Transactional
    public PresenceUpdate setUserOffline(Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    user.setStatus(UserStatus.OFFLINE);
                    user.setLastSeen(LocalDateTime.now());
                    userRepository.save(user);

                    lastHeartbeat.remove(userId);
                    clearTypingIndicators(userId);

                    log.info("User {} is now offline", user.getUsername());
                    return PresenceUpdate.offline(userId, user.getFullName());
                })
                .orElse(null);
    }

    @Transactional
    public PresenceUpdate updateStatus(Long userId, UserStatus status, String statusMessage) {
        return userRepository.findById(userId)
                .map(user -> {
                    user.setStatus(status);
                    user.setStatusMessage(statusMessage);
                    user.setLastActivity(LocalDateTime.now());
                    userRepository.save(user);

                    log.info("User {} status changed to {}", user.getUsername(), status);
                    return PresenceUpdate.statusChange(userId, user.getFullName(), status, statusMessage);
                })
                .orElse(null);
    }

    @Transactional
    public void recordHeartbeat(Long userId) {
        lastHeartbeat.put(userId, LocalDateTime.now());
        userRepository.updateLastActivity(userId, LocalDateTime.now());
    }

    @Transactional
    public List<PresenceUpdate> checkInactiveUsers(int timeoutMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);

        return lastHeartbeat.entrySet().stream()
                .filter(entry -> entry.getValue().isBefore(threshold))
                .map(entry -> {
                    Long userId = entry.getKey();
                    lastHeartbeat.remove(userId);
                    return setUserAway(userId);
                })
                .filter(update -> update != null)
                .collect(Collectors.toList());
    }

    @Transactional
    public PresenceUpdate setUserAway(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ONLINE)
                .map(user -> {
                    user.setStatus(UserStatus.AWAY);
                    userRepository.save(user);

                    log.debug("User {} is now away (inactive)", user.getUsername());
                    return PresenceUpdate.statusChange(userId, user.getFullName(), UserStatus.AWAY, null);
                })
                .orElse(null);
    }

    // Typing indicator methods
    public void setTyping(Long channelId, Long userId, boolean isTyping) {
        if (isTyping) {
            typingIndicators
                    .computeIfAbsent(channelId, k -> new ConcurrentHashMap<>())
                    .put(userId, LocalDateTime.now());
        } else {
            Map<Long, LocalDateTime> channelTyping = typingIndicators.get(channelId);
            if (channelTyping != null) {
                channelTyping.remove(userId);
            }
        }
    }

    public List<Long> getTypingUsers(Long channelId) {
        Map<Long, LocalDateTime> channelTyping = typingIndicators.get(channelId);
        if (channelTyping == null) {
            return List.of();
        }

        LocalDateTime threshold = LocalDateTime.now().minusSeconds(5);
        return channelTyping.entrySet().stream()
                .filter(entry -> entry.getValue().isAfter(threshold))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public void clearTypingIndicators(Long userId) {
        typingIndicators.values().forEach(map -> map.remove(userId));
    }

    public void cleanupTypingIndicators() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(10);
        typingIndicators.forEach((channelId, users) ->
                users.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold)));
    }

    public long getOnlineUserCount() {
        return userRepository.countOnlineUsers();
    }

    public List<Long> getActiveUserIds() {
        return sessionRepository.findDistinctActiveUserIds();
    }
}
