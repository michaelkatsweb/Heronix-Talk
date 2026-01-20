package com.heronix.talk.service;

import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.domain.UserSession;
import com.heronix.talk.model.dto.AuthRequest;
import com.heronix.talk.model.dto.AuthResponse;
import com.heronix.talk.model.dto.UserDTO;
import com.heronix.talk.model.enums.UserStatus;
import com.heronix.talk.repository.UserRepository;
import com.heronix.talk.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for authentication operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final UserService userService;
    private final ChannelService channelService;

    @Value("${heronix.session.timeout-hours:24}")
    private int sessionTimeoutHours;

    @Value("${heronix.session.remember-me-days:30}")
    private int rememberMeDays;

    @Transactional
    public AuthResponse authenticate(AuthRequest request, String ipAddress, String userAgent) {
        log.info("Authentication attempt for user: {}", request.getUsername());

        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());

        if (userOpt.isEmpty()) {
            // Try by employee ID
            userOpt = userRepository.findByEmployeeId(request.getUsername());
        }

        if (userOpt.isEmpty()) {
            log.warn("Authentication failed: user not found - {}", request.getUsername());
            return AuthResponse.failure("Invalid username or password");
        }

        User user = userOpt.get();

        if (!user.isActive()) {
            log.warn("Authentication failed: user deactivated - {}", request.getUsername());
            return AuthResponse.failure("Account is deactivated");
        }

        if (!userService.validatePassword(user, request.getPassword())) {
            log.warn("Authentication failed: invalid password - {}", request.getUsername());
            return AuthResponse.failure("Invalid username or password");
        }

        // Create session
        LocalDateTime expiresAt = request.isRememberMe()
                ? LocalDateTime.now().plusDays(rememberMeDays)
                : LocalDateTime.now().plusHours(sessionTimeoutHours);

        UserSession session = UserSession.builder()
                .user(user)
                .sessionToken(UserSession.generateToken())
                .clientType(request.getClientType())
                .clientVersion(request.getClientVersion())
                .deviceName(request.getDeviceName())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .rememberMe(request.isRememberMe())
                .expiresAt(expiresAt)
                .build();

        session = sessionRepository.save(session);

        // Update user status
        user.setStatus(UserStatus.ONLINE);
        user.setLastActivity(LocalDateTime.now());
        userRepository.save(user);

        // Auto-join public and announcement channels
        channelService.autoJoinPublicChannels(user);

        log.info("User {} authenticated successfully", user.getUsername());

        return AuthResponse.success(
                session.getSessionToken(),
                UserDTO.fromEntity(user),
                expiresAt
        );
    }

    @Transactional
    public boolean validateSession(String sessionToken) {
        return sessionRepository.findBySessionToken(sessionToken)
                .filter(session -> session.isActive() && !session.isExpired())
                .map(session -> {
                    session.updateActivity();
                    sessionRepository.save(session);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserFromSession(String sessionToken) {
        return sessionRepository.findBySessionTokenWithUser(sessionToken)
                .filter(session -> session.isActive() && !session.isExpired())
                .map(UserSession::getUser);
    }

    public Optional<UserSession> getSession(String sessionToken) {
        return sessionRepository.findBySessionToken(sessionToken)
                .filter(session -> session.isActive() && !session.isExpired());
    }

    @Transactional
    public void logout(String sessionToken) {
        sessionRepository.findBySessionToken(sessionToken).ifPresent(session -> {
            session.disconnect();
            sessionRepository.save(session);

            // Check if user has other active sessions
            long activeSessions = sessionRepository.countByUserIdAndActiveTrue(session.getUser().getId());
            if (activeSessions == 0) {
                userService.setUserOffline(session.getUser().getId());
            }

            log.info("User {} logged out", session.getUser().getUsername());
        });
    }

    @Transactional
    public void logoutAllSessions(Long userId) {
        sessionRepository.deactivateAllUserSessions(userId, LocalDateTime.now());
        userService.setUserOffline(userId);
        log.info("All sessions terminated for user {}", userId);
    }

    @Transactional
    public void updateWebsocketSession(String sessionToken, String websocketSessionId) {
        sessionRepository.updateWebsocketSessionId(sessionToken, websocketSessionId);
    }

    @Transactional
    public void handleWebsocketDisconnect(String websocketSessionId) {
        sessionRepository.findByWebsocketSessionId(websocketSessionId).ifPresent(session -> {
            sessionRepository.updateWebsocketSessionId(session.getSessionToken(), null);

            // Check if user has other active websocket sessions
            long activeWsSessions = sessionRepository.findActiveWebsocketSessionsByUserId(session.getUser().getId())
                    .stream()
                    .filter(s -> !s.getWebsocketSessionId().equals(websocketSessionId))
                    .count();

            if (activeWsSessions == 0) {
                userService.updateStatus(session.getUser().getId(), UserStatus.AWAY);
            }
        });
    }

    @Transactional
    public int cleanupStaleSessions(int timeoutMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        int deactivated = sessionRepository.deactivateStaleSessions(threshold, LocalDateTime.now());
        if (deactivated > 0) {
            log.info("Cleaned up {} stale sessions", deactivated);
        }
        return deactivated;
    }
}
