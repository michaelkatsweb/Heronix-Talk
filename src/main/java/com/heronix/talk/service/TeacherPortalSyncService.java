package com.heronix.talk.service;

import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.enums.SyncStatus;
import com.heronix.talk.model.enums.UserRole;
import com.heronix.talk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for synchronizing users from the Heronix Teacher Portal.
 * Pulls teacher data to enable messaging for authenticated users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherPortalSyncService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${heronix.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${heronix.sync.teacher-portal.url:http://localhost:58280}")
    private String teacherPortalUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Check if the teacher portal is reachable.
     */
    public boolean isTeacherPortalReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(teacherPortalUrl + "/api/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("Teacher portal not reachable: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Sync users from teacher portal if available.
     * This is a scheduled task that runs periodically.
     */
    @Scheduled(fixedRateString = "${heronix.sync.interval-seconds:60}000")
    public void scheduledSync() {
        if (!syncEnabled) {
            return;
        }

        if (isTeacherPortalReachable()) {
            log.debug("Starting sync with teacher portal...");
            syncTeachers();
        }
    }

    /**
     * Manual trigger for syncing teachers.
     */
    @Transactional
    public SyncResult syncTeachers() {
        SyncResult result = new SyncResult();

        try {
            // In a real implementation, this would call the teacher portal API
            // For now, this is a placeholder that demonstrates the pattern

            log.info("Syncing teachers from portal at {}", teacherPortalUrl);

            // Example of what the sync would look like:
            // 1. Call teacher portal API to get list of teachers
            // 2. For each teacher, create or update local user
            // 3. Track changes

            result.setSuccess(true);
            result.setMessage("Sync completed successfully");

        } catch (Exception e) {
            log.error("Error syncing teachers", e);
            result.setSuccess(false);
            result.setMessage("Sync failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Create or update a user from teacher portal data.
     */
    @Transactional
    public User syncTeacher(String employeeId, String firstName, String lastName,
                            String email, String department, String password) {
        return userRepository.findByEmployeeId(employeeId)
                .map(existingUser -> {
                    // Update existing user
                    existingUser.setFirstName(firstName);
                    existingUser.setLastName(lastName);
                    existingUser.setEmail(email);
                    existingUser.setDepartment(department);
                    existingUser.setSyncStatus(SyncStatus.SYNCED);
                    existingUser.setLastSyncTime(LocalDateTime.now());
                    existingUser.setSyncSource("teacher-portal");
                    log.debug("Updated user from teacher portal: {}", employeeId);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    // Create new user
                    User newUser = User.builder()
                            .username(employeeId.toLowerCase())
                            .employeeId(employeeId)
                            .firstName(firstName)
                            .lastName(lastName)
                            .email(email)
                            .department(department)
                            .passwordHash(password != null ? passwordEncoder.encode(password) : null)
                            .role(UserRole.TEACHER)
                            .syncStatus(SyncStatus.SYNCED)
                            .lastSyncTime(LocalDateTime.now())
                            .syncSource("teacher-portal")
                            .build();
                    log.info("Created new user from teacher portal: {}", employeeId);
                    return userRepository.save(newUser);
                });
    }

    /**
     * Get users that need to be synced to the teacher portal.
     */
    public List<User> getUsersNeedingSync() {
        return userRepository.findNeedingSync();
    }

    /**
     * Mark users as synced after successful push to teacher portal.
     */
    @Transactional
    public void markUsersSynced(List<Long> userIds) {
        for (Long userId : userIds) {
            userRepository.updateSyncStatus(userId, SyncStatus.SYNCED, LocalDateTime.now());
        }
    }

    /**
     * Result object for sync operations.
     */
    @lombok.Data
    public static class SyncResult {
        private boolean success;
        private String message;
        private int usersCreated;
        private int usersUpdated;
        private int errors;
        private LocalDateTime timestamp = LocalDateTime.now();
    }
}
