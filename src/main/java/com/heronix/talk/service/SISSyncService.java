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
 * Also handles periodic validation of user accounts against the SIS.
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

    // Track last validation time to avoid excessive API calls
    private LocalDateTime lastValidationTime = null;

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

        // Sync password hash from SIS if available (allows password changes to propagate)
        if (sisUser.getPassword() != null && !sisUser.getPassword().isBlank()) {
            // Only update if password has changed
            if (!sisUser.getPassword().equals(user.getPasswordHash())) {
                user.setPasswordHash(sisUser.getPassword());
                user.setPasswordChangeRequired(false);
                log.debug("Updated password hash from SIS for user: {}", user.getEmployeeId());
            }
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

        // Use password hash from SIS if available, otherwise generate temporary
        String passwordHash;
        boolean passwordChangeRequired;
        if (sisUser.getPassword() != null && !sisUser.getPassword().isBlank()) {
            // Use the BCrypt hash directly from SIS
            passwordHash = sisUser.getPassword();
            passwordChangeRequired = false;
            log.debug("Using password hash from SIS for user: {}", effectiveEmployeeId);
        } else {
            // Generate temporary password
            passwordHash = passwordEncoder.encode(generateTemporaryPassword(sisUser, effectiveEmployeeId));
            passwordChangeRequired = true;
            log.debug("Generated temporary password for user: {}", effectiveEmployeeId);
        }

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
                .passwordHash(passwordHash)
                .passwordChangeRequired(passwordChangeRequired)
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

    // ========================================================================
    // ACCOUNT VALIDATION - Periodic check against SIS
    // ========================================================================

    /**
     * Scheduled task to validate user accounts against the SIS.
     * Runs every 15 minutes (configurable) to check if accounts are still active.
     * If an account is deactivated in SIS, it will be deactivated in Talk as well.
     */
    @Scheduled(fixedDelayString = "${heronix.sis.validation.interval-seconds:900}000")
    public void scheduledAccountValidation() {
        if (!sisProperties.getApi().isEnabled()) {
            return;
        }

        // Check if validation is enabled (default: true)
        if (!sisProperties.getSync().isValidationEnabled()) {
            return;
        }

        log.debug("Running scheduled SIS account validation...");
        try {
            ValidationResultDTO result = validateAllSyncedUsers();
            if (result.getTotalChecked() > 0) {
                log.info("SIS account validation completed: {} checked, {} deactivated, {} reactivated, {} errors",
                        result.getTotalChecked(), result.getDeactivated(),
                        result.getReactivated(), result.getErrors());
            }
            lastValidationTime = LocalDateTime.now();
        } catch (Exception e) {
            log.error("Scheduled SIS account validation failed", e);
        }
    }

    /**
     * Manually trigger account validation for all synced users.
     */
    public ValidationResultDTO validateAllSyncedUsers() {
        ValidationResultDTO result = new ValidationResultDTO();

        // Get all users that were synced from SIS
        List<User> syncedUsers = userRepository.findBySyncSourceContaining("SIS");

        log.info("Validating {} SIS-synced user accounts", syncedUsers.size());

        for (User user : syncedUsers) {
            try {
                validateUserAccount(user, result);
            } catch (Exception e) {
                log.error("Error validating user {}: {}", user.getEmployeeId(), e.getMessage());
                result.incrementErrors();
            }
        }

        auditService.logAdminAction(null, "SIS_VALIDATION",
                String.format("Account validation completed: %d checked, %d deactivated, %d reactivated",
                        result.getTotalChecked(), result.getDeactivated(), result.getReactivated()));

        return result;
    }

    /**
     * Validate a single user account against the SIS.
     */
    private void validateUserAccount(User user, ValidationResultDTO result) {
        result.incrementChecked();

        if (user.getEmployeeId() == null || user.getEmployeeId().isBlank()) {
            log.debug("Skipping user {} - no employee ID", user.getUsername());
            return;
        }

        try {
            // Fetch user status from SIS
            String url = sisProperties.getApi().getBaseUrl() + "/api/teacher/employee/" + user.getEmployeeId();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(sisProperties.getApi().getTimeoutSeconds()))
                    .GET();

            // Add authorization token if configured
            String token = sisProperties.getApi().getToken();
            if (token != null && !token.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                // User not found in SIS - deactivate in Talk
                if (user.isActive()) {
                    log.warn("User {} not found in SIS, deactivating Talk account", user.getEmployeeId());
                    deactivateUser(user, "User not found in SIS");
                    result.incrementDeactivated();
                }
                return;
            }

            if (response.statusCode() != 200) {
                log.warn("Failed to validate user {} - SIS returned status {}",
                        user.getEmployeeId(), response.statusCode());
                result.incrementErrors();
                return;
            }

            // Parse response and check active status
            com.fasterxml.jackson.databind.JsonNode sisData = objectMapper.readTree(response.body());
            boolean sisActive = sisData.has("active") && sisData.get("active").asBoolean(true);

            if (!sisActive && user.isActive()) {
                // SIS account is inactive, deactivate Talk account
                log.info("User {} is inactive in SIS, deactivating Talk account", user.getEmployeeId());
                deactivateUser(user, "Account deactivated in SIS");
                result.incrementDeactivated();
            } else if (sisActive && !user.isActive()) {
                // SIS account is active but Talk account is inactive - reactivate
                log.info("User {} is active in SIS, reactivating Talk account", user.getEmployeeId());
                reactivateUser(user, sisData);
                result.incrementReactivated();
            } else {
                // Status matches, optionally update other fields
                updateUserFromSis(user, sisData);
            }

        } catch (Exception e) {
            log.error("Error validating user {} against SIS", user.getEmployeeId(), e);
            result.incrementErrors();
        }
    }

    /**
     * Deactivate a user account in Talk.
     */
    @Transactional
    public void deactivateUser(User user, String reason) {
        user.setActive(false);
        user.setStatus(com.heronix.talk.model.enums.UserStatus.OFFLINE);
        user.setLastSyncTime(LocalDateTime.now());
        userRepository.save(user);

        auditService.logAdminAction(null, "USER_DEACTIVATED",
                String.format("User %s (%s) deactivated: %s",
                        user.getUsername(), user.getEmployeeId(), reason));

        log.info("Deactivated user {} ({}): {}", user.getUsername(), user.getEmployeeId(), reason);
    }

    /**
     * Reactivate a user account in Talk based on SIS data.
     */
    @Transactional
    public void reactivateUser(User user, com.fasterxml.jackson.databind.JsonNode sisData) {
        user.setActive(true);
        user.setLastSyncTime(LocalDateTime.now());

        // Update fields from SIS
        updateUserFromSis(user, sisData);

        userRepository.save(user);

        auditService.logAdminAction(null, "USER_REACTIVATED",
                String.format("User %s (%s) reactivated from SIS",
                        user.getUsername(), user.getEmployeeId()));

        log.info("Reactivated user {} ({}) from SIS", user.getUsername(), user.getEmployeeId());
    }

    /**
     * Update user fields from SIS data.
     */
    private void updateUserFromSis(User user, com.fasterxml.jackson.databind.JsonNode sisData) {
        if (sisData.has("firstName") && !sisData.get("firstName").isNull()) {
            user.setFirstName(sisData.get("firstName").asText());
        }
        if (sisData.has("lastName") && !sisData.get("lastName").isNull()) {
            user.setLastName(sisData.get("lastName").asText());
        }
        if (sisData.has("email") && !sisData.get("email").isNull()) {
            user.setEmail(sisData.get("email").asText());
        }
        if (sisData.has("department") && !sisData.get("department").isNull()) {
            user.setDepartment(sisData.get("department").asText());
        }
        if (sisData.has("phoneNumber") && !sisData.get("phoneNumber").isNull()) {
            user.setPhoneNumber(sisData.get("phoneNumber").asText());
        }
        if (sisData.has("role") && !sisData.get("role").isNull()) {
            user.setRole(mapRole(sisData.get("role").asText()));
        }

        user.setLastSyncTime(LocalDateTime.now());
        user.setSyncStatus(SyncStatus.SYNCED);
        userRepository.save(user);
    }

    /**
     * Validate a specific user by employee ID.
     */
    public ValidationResultDTO validateUser(String employeeId) {
        ValidationResultDTO result = new ValidationResultDTO();

        Optional<User> userOpt = userRepository.findByEmployeeId(employeeId);
        if (userOpt.isEmpty()) {
            result.addError("User not found: " + employeeId);
            return result;
        }

        validateUserAccount(userOpt.get(), result);
        return result;
    }

    /**
     * Get the last validation time.
     */
    public LocalDateTime getLastValidationTime() {
        return lastValidationTime;
    }

    /**
     * DTO for validation results.
     */
    @lombok.Data
    public static class ValidationResultDTO {
        private int totalChecked = 0;
        private int deactivated = 0;
        private int reactivated = 0;
        private int errors = 0;
        private List<String> errorMessages = new java.util.ArrayList<>();

        public void incrementChecked() { totalChecked++; }
        public void incrementDeactivated() { deactivated++; }
        public void incrementReactivated() { reactivated++; }
        public void incrementErrors() { errors++; }
        public void addError(String message) {
            errors++;
            errorMessages.add(message);
        }
    }
}
