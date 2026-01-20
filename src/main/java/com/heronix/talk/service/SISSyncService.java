package com.heronix.talk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heronix.talk.config.SisIntegrationProperties;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.ImportResultDTO;
import com.heronix.talk.model.dto.SisUserDTO;
import com.heronix.talk.model.enums.SyncStatus;
import com.heronix.talk.model.enums.UserRole;
import com.heronix.talk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Optional;

/**
 * Service for synchronizing users from the SIS (Student Information System) via REST API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SisSyncService {

    private final SisIntegrationProperties sisProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Scheduled sync task - runs if automatic sync is enabled.
     */
    @Scheduled(fixedDelayString = "${heronix.sis.sync.interval-seconds:300}000")
    public void scheduledSync() {
        if (!sisProperties.getSync().isEnabled() || !sisProperties.getApi().isEnabled()) {
            return;
        }

        log.debug("Running scheduled SIS sync...");
        try {
            ImportResultDTO result = syncFromApi();
            if (result.isSuccess() && result.getTotalProcessed() > 0) {
                log.info("Scheduled SIS sync completed: {} processed, {} created, {} updated",
                        result.getTotalProcessed(), result.getCreated(), result.getUpdated());
            }
        } catch (Exception e) {
            log.error("Scheduled SIS sync failed", e);
        }
    }

    /**
     * Manually trigger a sync from the SIS API.
     */
    public ImportResultDTO syncFromApi() {
        long startTime = System.currentTimeMillis();
        String source = "SIS API: " + sisProperties.getApi().getBaseUrl();

        if (!sisProperties.getApi().isEnabled()) {
            return ImportResultDTO.createError(source, "SIS API sync is disabled");
        }

        try {
            List<SisUserDTO> users = fetchUsersFromApi();
            ImportResultDTO result = processUsers(users, source);
            result.setDurationMs(System.currentTimeMillis() - startTime);
            result.setSuccess(result.getErrors() == 0);

            auditService.logAdminAction(null, "SIS_SYNC",
                    String.format("API sync completed: %d processed, %d created, %d updated, %d errors",
                            result.getTotalProcessed(), result.getCreated(),
                            result.getUpdated(), result.getErrors()));

            return result;
        } catch (Exception e) {
            log.error("Failed to sync from SIS API", e);
            return ImportResultDTO.createError(source, "API sync failed: " + e.getMessage());
        }
    }

    /**
     * Fetch users from the SIS API.
     */
    private List<SisUserDTO> fetchUsersFromApi() throws Exception {
        String url = sisProperties.getApi().getBaseUrl() + sisProperties.getApi().getEndpoint();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(sisProperties.getApi().getTimeoutSeconds()))
                .GET();

        // Add authorization token if configured
        String token = sisProperties.getApi().getToken();
        if (token != null && !token.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        HttpRequest request = requestBuilder.build();
        log.debug("Fetching users from SIS API: {}", url);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("SIS API returned status " + response.statusCode() + ": " + response.body());
        }

        return objectMapper.readValue(response.body(), new TypeReference<List<SisUserDTO>>() {});
    }

    /**
     * Process a list of SIS users and sync them to the local database.
     */
    @Transactional
    public ImportResultDTO processUsers(List<SisUserDTO> sisUsers, String source) {
        ImportResultDTO result = ImportResultDTO.builder()
                .source(source)
                .timestamp(LocalDateTime.now())
                .build();

        for (SisUserDTO sisUser : sisUsers) {
            try {
                // Parse full name into first/last if needed
                sisUser.parseFullNameIfNeeded();

                if (!sisUser.isValid()) {
                    result.addWarning("Skipped invalid user: " + sisUser.getEffectiveEmployeeId());
                    result.incrementSkipped();
                    continue;
                }

                processUser(sisUser, result);
            } catch (Exception e) {
                log.error("Error processing SIS user: {}", sisUser.getEffectiveEmployeeId(), e);
                result.addError("Failed to process user " + sisUser.getEffectiveEmployeeId() + ": " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * Process a single SIS user - create or update in local database.
     */
    private void processUser(SisUserDTO sisUser, ImportResultDTO result) {
        String effectiveEmployeeId = sisUser.getEffectiveEmployeeId();
        Optional<User> existingUser = userRepository.findByEmployeeId(effectiveEmployeeId);

        if (existingUser.isPresent()) {
            updateExistingUser(existingUser.get(), sisUser);
            result.incrementUpdated();
            log.debug("Updated user from SIS: {}", effectiveEmployeeId);
        } else {
            createNewUser(sisUser);
            result.incrementCreated();
            log.debug("Created user from SIS: {}", effectiveEmployeeId);
        }
    }

    /**
     * Update an existing user with data from SIS.
     */
    private void updateExistingUser(User user, SisUserDTO sisUser) {
        user.setFirstName(sisUser.getFirstName());
        user.setLastName(sisUser.getLastName());

        if (sisUser.getEmail() != null && !sisUser.getEmail().isBlank()) {
            user.setEmail(sisUser.getEmail());
        }
        if (sisUser.getDepartment() != null) {
            user.setDepartment(sisUser.getDepartment());
        }
        if (sisUser.getPhoneNumber() != null) {
            user.setPhoneNumber(sisUser.getPhoneNumber());
        }
        if (sisUser.getRole() != null) {
            user.setRole(mapRole(sisUser.getRole()));
        }

        user.setActive(sisUser.isActive());
        user.setSyncStatus(SyncStatus.SYNCED);
        user.setLastSyncTime(LocalDateTime.now());
        user.setSyncSource("SIS");

        userRepository.save(user);
    }

    /**
     * Create a new user from SIS data.
     */
    private void createNewUser(SisUserDTO sisUser) {
        String username = generateUsername(sisUser);
        String effectiveEmployeeId = sisUser.getEffectiveEmployeeId();

        User user = User.builder()
                .employeeId(effectiveEmployeeId)
                .username(username)
                .firstName(sisUser.getFirstName())
                .lastName(sisUser.getLastName())
                .email(sisUser.getEmail())
                .department(sisUser.getDepartment() != null ? sisUser.getDepartment() : sisUser.getSubject())
                .phoneNumber(sisUser.getPhoneNumber())
                .role(mapRole(sisUser.getRole()))
                .active(sisUser.isActive())
                .passwordHash(passwordEncoder.encode(generateTemporaryPassword(sisUser, effectiveEmployeeId)))
                .passwordChangeRequired(true)
                .syncStatus(SyncStatus.SYNCED)
                .lastSyncTime(LocalDateTime.now())
                .syncSource("SIS")
                .build();

        userRepository.save(user);
    }

    /**
     * Generate a username from SIS user data.
     */
    private String generateUsername(SisUserDTO sisUser) {
        // Try username from SIS first
        if (sisUser.getUsername() != null && !sisUser.getUsername().isBlank()) {
            String username = sisUser.getUsername().toLowerCase().trim();
            if (!userRepository.existsByUsername(username)) {
                return username;
            }
        }

        // Try email prefix
        if (sisUser.getEmail() != null && sisUser.getEmail().contains("@")) {
            String emailPrefix = sisUser.getEmail().split("@")[0].toLowerCase();
            if (!userRepository.existsByUsername(emailPrefix)) {
                return emailPrefix;
            }
        }

        // Generate from name
        String baseUsername = (sisUser.getFirstName().charAt(0) + sisUser.getLastName())
                .toLowerCase().replaceAll("[^a-z0-9]", "");

        String username = baseUsername;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + counter++;
        }

        return username;
    }

    /**
     * Generate a temporary password for new users.
     */
    private String generateTemporaryPassword(SisUserDTO sisUser, String employeeId) {
        // Use employee ID + first 3 chars of last name as temp password
        String lastName = sisUser.getLastName() != null ? sisUser.getLastName() : "user";
        return employeeId + lastName.substring(0, Math.min(3, lastName.length())).toLowerCase();
    }

    /**
     * Map SIS role string to UserRole enum.
     */
    private UserRole mapRole(String sisRole) {
        if (sisRole == null || sisRole.isBlank()) {
            return UserRole.TEACHER;
        }

        return switch (sisRole.toUpperCase()) {
            case "ADMIN", "ADMINISTRATOR" -> UserRole.ADMIN;
            case "PRINCIPAL" -> UserRole.PRINCIPAL;
            case "COUNSELOR" -> UserRole.COUNSELOR;
            case "DEPARTMENT_HEAD", "DEPT_HEAD" -> UserRole.DEPARTMENT_HEAD;
            case "STAFF" -> UserRole.STAFF;
            default -> UserRole.TEACHER;
        };
    }

    /**
     * Test connection to the SIS API.
     */
    public boolean testConnection() {
        try {
            String url = sisProperties.getApi().getBaseUrl() + "/health";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("SIS API connection test failed", e);
            return false;
        }
    }

    /**
     * Get current SIS sync configuration status.
     */
    public SisSyncStatusDTO getStatus() {
        return SisSyncStatusDTO.builder()
                .syncEnabled(sisProperties.getSync().isEnabled())
                .apiEnabled(sisProperties.getApi().isEnabled())
                .baseUrl(sisProperties.getApi().getBaseUrl())
                .endpoint(sisProperties.getApi().getEndpoint())
                .intervalSeconds(sisProperties.getSync().getIntervalSeconds())
                .connectionOk(testConnection())
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class SisSyncStatusDTO {
        private boolean syncEnabled;
        private boolean apiEnabled;
        private String baseUrl;
        private String endpoint;
        private int intervalSeconds;
        private boolean connectionOk;
    }
}
