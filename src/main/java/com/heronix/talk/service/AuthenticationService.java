package com.heronix.talk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.domain.UserSession;
import com.heronix.talk.model.dto.AuthRequest;
import com.heronix.talk.model.dto.AuthResponse;
import com.heronix.talk.model.dto.UserDTO;
import com.heronix.talk.model.enums.SyncStatus;
import com.heronix.talk.model.enums.UserRole;
import com.heronix.talk.model.enums.UserStatus;
import com.heronix.talk.repository.UserRepository;
import com.heronix.talk.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Value("${heronix.session.timeout-hours:24}")
    private int sessionTimeoutHours;

    @Value("${heronix.session.remember-me-days:30}")
    private int rememberMeDays;

    @Value("${heronix.sis.api.enabled:true}")
    private boolean sisApiEnabled;

    @Value("${heronix.sis.api.base-url:http://localhost:9580}")
    private String sisBaseUrl;

    @Value("${heronix.sis.auto-register.enabled:true}")
    private boolean autoRegisterEnabled;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Transactional
    public AuthResponse authenticate(AuthRequest request, String ipAddress, String userAgent) {
        log.info("Authentication attempt for user: {}", request.getUsername());

        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());

        if (userOpt.isEmpty()) {
            // Try by employee ID
            userOpt = userRepository.findByEmployeeId(request.getUsername());
        }

        if (userOpt.isEmpty()) {
            // User not found locally - try to auto-register from SIS
            if (autoRegisterEnabled && sisApiEnabled) {
                log.info("User not found locally, attempting auto-registration from SIS: {}", request.getUsername());
                userOpt = tryAutoRegisterFromSis(request.getUsername(), request.getPassword());
            }

            if (userOpt.isEmpty()) {
                log.warn("Authentication failed: user not found - {}", request.getUsername());
                return AuthResponse.failure("Invalid username or password");
            }
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

    /**
     * Try to auto-register a user from the SIS (Heronix-Application).
     * This validates the user's credentials against the SIS and creates a local account if valid.
     */
    private Optional<User> tryAutoRegisterFromSis(String username, String password) {
        try {
            // First, try to fetch teacher data from SIS by employee ID
            String teacherUrl = sisBaseUrl + "/api/teacher/employee/" + username;
            log.debug("Fetching teacher from SIS: {}", teacherUrl);

            HttpRequest teacherRequest = HttpRequest.newBuilder()
                    .uri(URI.create(teacherUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> teacherResponse = httpClient.send(teacherRequest, HttpResponse.BodyHandlers.ofString());

            if (teacherResponse.statusCode() != 200) {
                log.debug("Teacher not found in SIS: {}", username);
                return Optional.empty();
            }

            JsonNode teacherData = objectMapper.readTree(teacherResponse.body());

            // Validate password by attempting to authenticate with SIS
            // The SIS stores BCrypt hashed passwords, so we need to verify locally
            String storedPasswordHash = getJsonString(teacherData, "password");
            if (storedPasswordHash == null || storedPasswordHash.isBlank()) {
                log.debug("Teacher has no password set in SIS: {}", username);
                return Optional.empty();
            }

            // Verify the password against the hash from SIS
            if (!passwordEncoder.matches(password, storedPasswordHash)) {
                log.debug("Password validation failed for SIS user: {}", username);
                return Optional.empty();
            }

            // Password is valid - create local user account
            String employeeId = getJsonString(teacherData, "employeeId");
            String firstName = getJsonString(teacherData, "firstName");
            String lastName = getJsonString(teacherData, "lastName");
            String email = getJsonString(teacherData, "email");
            String department = getJsonString(teacherData, "department");
            String phoneNumber = getJsonString(teacherData, "phoneNumber");
            String roleName = getJsonString(teacherData, "role");

            // Generate unique username
            String generatedUsername = generateUsername(employeeId, firstName, lastName, email);

            User newUser = User.builder()
                    .employeeId(employeeId != null ? employeeId : username)
                    .username(generatedUsername)
                    .firstName(firstName != null ? firstName : "User")
                    .lastName(lastName != null ? lastName : username)
                    .email(email)
                    .department(department)
                    .phoneNumber(phoneNumber)
                    .role(mapSisRole(roleName))
                    .active(true)
                    .passwordHash(storedPasswordHash) // Use the same hash from SIS
                    .syncStatus(SyncStatus.SYNCED)
                    .lastSyncTime(LocalDateTime.now())
                    .syncSource("SIS-AUTO")
                    .build();

            User savedUser = userRepository.save(newUser);
            log.info("Auto-registered user from SIS: {} ({})", savedUser.getUsername(), savedUser.getEmployeeId());

            return Optional.of(savedUser);

        } catch (Exception e) {
            log.error("Error during SIS auto-registration for user: {}", username, e);
            return Optional.empty();
        }
    }

    private String getJsonString(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode != null && !fieldNode.isNull()) {
            return fieldNode.asText();
        }
        return null;
    }

    private String generateUsername(String employeeId, String firstName, String lastName, String email) {
        // Try employee ID first
        if (employeeId != null && !employeeId.isBlank()) {
            String username = employeeId.toLowerCase().trim();
            if (!userRepository.existsByUsername(username)) {
                return username;
            }
        }

        // Try email prefix
        if (email != null && email.contains("@")) {
            String emailPrefix = email.split("@")[0].toLowerCase();
            if (!userRepository.existsByUsername(emailPrefix)) {
                return emailPrefix;
            }
        }

        // Generate from name
        String baseUsername = ((firstName != null ? firstName.charAt(0) : "u") +
                (lastName != null ? lastName : "ser")).toLowerCase().replaceAll("[^a-z0-9]", "");

        String username = baseUsername;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + counter++;
        }

        return username;
    }

    private UserRole mapSisRole(String sisRole) {
        if (sisRole == null || sisRole.isBlank()) {
            return UserRole.TEACHER;
        }

        return switch (sisRole.toUpperCase()) {
            case "ADMIN", "ADMINISTRATOR" -> UserRole.ADMIN;
            case "PRINCIPAL" -> UserRole.PRINCIPAL;
            case "COUNSELOR" -> UserRole.COUNSELOR;
            case "DEPARTMENT_HEAD", "DEPT_HEAD", "LEAD_TEACHER" -> UserRole.DEPARTMENT_HEAD;
            case "STAFF" -> UserRole.STAFF;
            default -> UserRole.TEACHER;
        };
    }
}
