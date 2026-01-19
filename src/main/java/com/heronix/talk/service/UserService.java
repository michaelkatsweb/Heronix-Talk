package com.heronix.talk.service;

import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.BulkUserActionRequest;
import com.heronix.talk.model.dto.PasswordResetRequest;
import com.heronix.talk.model.dto.UserDTO;
import com.heronix.talk.model.enums.AuditAction;
import com.heronix.talk.model.enums.AuditCategory;
import com.heronix.talk.model.enums.SyncStatus;
import com.heronix.talk.model.enums.UserRole;
import com.heronix.talk.model.enums.UserStatus;
import com.heronix.talk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for user management operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByEmployeeId(String employeeId) {
        return userRepository.findByEmployeeId(employeeId);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public List<UserDTO> getAllActiveUsers() {
        return userRepository.findByActiveTrueOrderByLastNameAsc().stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<UserDTO> getOnlineUsers() {
        return userRepository.findOnlineUsers().stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<UserDTO> getUsersByRole(UserRole role) {
        return userRepository.findByRoleAndActiveTrue(role).stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<UserDTO> getUsersByDepartment(String department) {
        return userRepository.findByDepartmentAndActiveTrue(department).stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<UserDTO> searchUsers(String term) {
        return userRepository.searchActiveByName(term).stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public User createUser(User user) {
        log.info("Creating new user: {}", user.getUsername());

        if (user.getPasswordHash() != null) {
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        }

        user.setSyncStatus(SyncStatus.LOCAL_ONLY);
        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(User user) {
        log.info("Updating user: {}", user.getUsername());
        return userRepository.save(user);
    }

    @Transactional
    public void updateStatus(Long userId, UserStatus status) {
        log.debug("Updating user {} status to {}", userId, status);
        LocalDateTime lastSeen = status == UserStatus.OFFLINE ? LocalDateTime.now() : null;
        userRepository.updateStatus(userId, status, lastSeen);
    }

    @Transactional
    public void updateLastActivity(Long userId) {
        userRepository.updateLastActivity(userId, LocalDateTime.now());
    }

    @Transactional
    public void setUserOnline(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setStatus(UserStatus.ONLINE);
            user.setLastActivity(LocalDateTime.now());
            userRepository.save(user);
            log.info("User {} is now online", user.getUsername());
        });
    }

    @Transactional
    public void setUserOffline(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setStatus(UserStatus.OFFLINE);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);
            log.info("User {} is now offline", user.getUsername());
        });
    }

    @Transactional
    public void deactivateUser(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setActive(false);
            user.setStatus(UserStatus.OFFLINE);
            userRepository.save(user);
            log.info("User {} deactivated", user.getUsername());
        });
    }

    public boolean validatePassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    @Transactional
    public void changePassword(Long userId, String newPassword) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            log.info("Password changed for user {}", user.getUsername());
        });
    }

    public long getOnlineUserCount() {
        return userRepository.countOnlineUsers();
    }

    public long getTotalActiveUserCount() {
        return userRepository.countByActiveTrue();
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public List<User> getUsersNeedingSync() {
        return userRepository.findNeedingSync();
    }

    @Transactional
    public void markAsSynced(Long userId) {
        userRepository.updateSyncStatus(userId, SyncStatus.SYNCED, LocalDateTime.now());
    }

    // ==================== Admin Methods ====================

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void lockUser(Long userId, User admin) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLocked(true);
            user.setStatus(UserStatus.OFFLINE);
            userRepository.save(user);

            auditService.log(AuditCategory.USER_MANAGEMENT, AuditAction.ACCOUNT_LOCKED, admin,
                    "USER", userId, user.getUsername(),
                    "User account locked by admin");

            log.info("User {} locked by {}", user.getUsername(), admin.getUsername());
        });
    }

    @Transactional
    public void unlockUser(Long userId, User admin) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLocked(false);
            user.setFailedLoginAttempts(0);
            userRepository.save(user);

            auditService.log(AuditCategory.USER_MANAGEMENT, AuditAction.ACCOUNT_UNLOCKED, admin,
                    "USER", userId, user.getUsername(),
                    "User account unlocked by admin");

            log.info("User {} unlocked by {}", user.getUsername(), admin.getUsername());
        });
    }

    @Transactional
    public String resetPassword(Long userId, PasswordResetRequest request, User admin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String newPassword;
        if (request.getNewPassword() != null && !request.getNewPassword().isEmpty()) {
            newPassword = request.getNewPassword();
        } else {
            newPassword = generateTemporaryPassword();
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        if (request.isForceChangeOnLogin()) {
            user.setPasswordChangeRequired(true);
        }
        userRepository.save(user);

        auditService.log(AuditCategory.USER_MANAGEMENT, AuditAction.PASSWORD_RESET, admin,
                "USER", userId, user.getUsername(),
                "Password reset by admin. Reason: " + (request.getResetReason() != null ? request.getResetReason() : "Not specified"));

        log.info("Password reset for user {} by {}", user.getUsername(), admin.getUsername());
        return newPassword;
    }

    @Transactional
    public void updateUserRole(Long userId, String roleName, User admin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String oldRole = user.getRole();
        user.setRole(roleName);
        userRepository.save(user);

        auditService.log(AuditCategory.USER_MANAGEMENT, AuditAction.USER_ROLE_CHANGED, admin,
                "USER", userId, user.getUsername(),
                "User role changed from " + oldRole + " to " + roleName);

        log.info("User {} role changed from {} to {} by {}", user.getUsername(), oldRole, roleName, admin.getUsername());
    }

    @Transactional
    public int performBulkAction(BulkUserActionRequest request, User admin) {
        int affected = 0;

        for (Long userId : request.getUserIds()) {
            try {
                switch (request.getAction().toUpperCase()) {
                    case "LOCK" -> {
                        lockUser(userId, admin);
                        affected++;
                    }
                    case "UNLOCK" -> {
                        unlockUser(userId, admin);
                        affected++;
                    }
                    case "ACTIVATE" -> {
                        activateUser(userId, admin);
                        affected++;
                    }
                    case "DEACTIVATE" -> {
                        deactivateUserByAdmin(userId, admin);
                        affected++;
                    }
                    case "ASSIGN_ROLE" -> {
                        if (request.getRoleId() != null) {
                            // Would need to look up role name from roleId
                            affected++;
                        }
                    }
                    case "DELETE" -> {
                        deleteUser(userId, admin);
                        affected++;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to perform {} on user {}: {}", request.getAction(), userId, e.getMessage());
            }
        }

        log.info("Bulk action {} completed: {} users affected by {}", request.getAction(), affected, admin.getUsername());
        return affected;
    }

    @Transactional
    public void activateUser(Long userId, User admin) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setActive(true);
            userRepository.save(user);

            auditService.log(AuditCategory.USER_MANAGEMENT, AuditAction.USER_ACTIVATED, admin,
                    "USER", userId, user.getUsername(),
                    "User account activated by admin");

            log.info("User {} activated by {}", user.getUsername(), admin.getUsername());
        });
    }

    @Transactional
    public void deactivateUserByAdmin(Long userId, User admin) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setActive(false);
            user.setStatus(UserStatus.OFFLINE);
            userRepository.save(user);

            auditService.log(AuditCategory.USER_MANAGEMENT, AuditAction.USER_DEACTIVATED, admin,
                    "USER", userId, user.getUsername(),
                    "User account deactivated by admin");

            log.info("User {} deactivated by {}", user.getUsername(), admin.getUsername());
        });
    }

    @Transactional
    public void deleteUser(Long userId, User admin) {
        userRepository.findById(userId).ifPresent(user -> {
            String username = user.getUsername();
            userRepository.delete(user);

            auditService.log(AuditCategory.USER_MANAGEMENT, AuditAction.USER_DELETED, admin,
                    "USER", userId, username,
                    "User account deleted by admin");

            log.info("User {} deleted by {}", username, admin.getUsername());
        });
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
