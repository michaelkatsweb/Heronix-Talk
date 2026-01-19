package com.heronix.talk.service;

import com.heronix.talk.model.domain.PasswordHistory;
import com.heronix.talk.model.domain.SecurityPolicy;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.SecurityPolicyDTO;
import com.heronix.talk.model.dto.UpdateSecurityPolicyRequest;
import com.heronix.talk.model.enums.AuditAction;
import com.heronix.talk.model.enums.AuditCategory;
import com.heronix.talk.repository.PasswordHistoryRepository;
import com.heronix.talk.repository.SecurityPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Service for security policy management and password validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityPolicyService {

    private final SecurityPolicyRepository securityPolicyRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    // Common passwords list (abbreviated - in production, load from file)
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "123456", "12345678", "qwerty", "abc123", "monkey", "1234567",
            "letmein", "trustno1", "dragon", "baseball", "iloveyou", "master", "sunshine",
            "ashley", "bailey", "shadow", "123123", "654321", "superman", "qazwsx",
            "michael", "football", "password1", "password123", "welcome", "welcome1"
    );

    /**
     * Get the active security policy.
     */
    public SecurityPolicy getActivePolicy() {
        return securityPolicyRepository.findByDefaultPolicyTrue()
                .orElseGet(this::createDefaultPolicy);
    }

    public SecurityPolicyDTO getActivePolicyDTO() {
        return SecurityPolicyDTO.fromEntity(getActivePolicy());
    }

    public List<SecurityPolicyDTO> getAllPolicies() {
        return securityPolicyRepository.findAll().stream()
                .map(SecurityPolicyDTO::fromEntity)
                .toList();
    }

    public Optional<SecurityPolicyDTO> getPolicyById(Long id) {
        return securityPolicyRepository.findById(id)
                .map(SecurityPolicyDTO::fromEntity);
    }

    /**
     * Create or update a security policy.
     */
    @Transactional
    public SecurityPolicyDTO updatePolicy(Long id, UpdateSecurityPolicyRequest request, User admin) {
        SecurityPolicy policy = securityPolicyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found"));

        // Update fields if provided
        if (request.getMinPasswordLength() != null) {
            policy.setMinPasswordLength(request.getMinPasswordLength());
        }
        if (request.getMaxPasswordLength() != null) {
            policy.setMaxPasswordLength(request.getMaxPasswordLength());
        }
        if (request.getRequireUppercase() != null) {
            policy.setRequireUppercase(request.getRequireUppercase());
        }
        if (request.getRequireLowercase() != null) {
            policy.setRequireLowercase(request.getRequireLowercase());
        }
        if (request.getRequireNumbers() != null) {
            policy.setRequireNumbers(request.getRequireNumbers());
        }
        if (request.getRequireSpecialChars() != null) {
            policy.setRequireSpecialChars(request.getRequireSpecialChars());
        }
        if (request.getSpecialCharsAllowed() != null) {
            policy.setSpecialCharsAllowed(request.getSpecialCharsAllowed());
        }
        if (request.getPasswordExpiryDays() != null) {
            policy.setPasswordExpiryDays(request.getPasswordExpiryDays());
        }
        if (request.getPasswordHistoryCount() != null) {
            policy.setPasswordHistoryCount(request.getPasswordHistoryCount());
        }
        if (request.getPreventCommonPasswords() != null) {
            policy.setPreventCommonPasswords(request.getPreventCommonPasswords());
        }
        if (request.getMaxLoginAttempts() != null) {
            policy.setMaxLoginAttempts(request.getMaxLoginAttempts());
        }
        if (request.getLockoutDurationMinutes() != null) {
            policy.setLockoutDurationMinutes(request.getLockoutDurationMinutes());
        }
        if (request.getSessionTimeoutMinutes() != null) {
            policy.setSessionTimeoutMinutes(request.getSessionTimeoutMinutes());
        }
        if (request.getConcurrentSessionsAllowed() != null) {
            policy.setConcurrentSessionsAllowed(request.getConcurrentSessionsAllowed());
        }
        if (request.getRequireMfa() != null) {
            policy.setRequireMfa(request.getRequireMfa());
        }
        if (request.getIpWhitelistEnabled() != null) {
            policy.setIpWhitelistEnabled(request.getIpWhitelistEnabled());
        }
        if (request.getIpWhitelist() != null) {
            policy.setIpWhitelist(request.getIpWhitelist());
        }
        if (request.getIpBlacklist() != null) {
            policy.setIpBlacklist(request.getIpBlacklist());
        }
        if (request.getAuditLoginEvents() != null) {
            policy.setAuditLoginEvents(request.getAuditLoginEvents());
        }
        if (request.getAuditAdminActions() != null) {
            policy.setAuditAdminActions(request.getAuditAdminActions());
        }
        if (request.getAuditMessageEvents() != null) {
            policy.setAuditMessageEvents(request.getAuditMessageEvents());
        }
        if (request.getAuditRetentionDays() != null) {
            policy.setAuditRetentionDays(request.getAuditRetentionDays());
        }
        if (request.getRateLimitEnabled() != null) {
            policy.setRateLimitEnabled(request.getRateLimitEnabled());
        }
        if (request.getRateLimitRequestsPerMinute() != null) {
            policy.setRateLimitRequestsPerMinute(request.getRateLimitRequestsPerMinute());
        }
        if (request.getRateLimitMessagesPerMinute() != null) {
            policy.setRateLimitMessagesPerMinute(request.getRateLimitMessagesPerMinute());
        }

        SecurityPolicy saved = securityPolicyRepository.save(policy);

        auditService.log(AuditCategory.SECURITY_POLICY, AuditAction.SECURITY_POLICY_UPDATED, admin,
                "SECURITY_POLICY", saved.getId(), saved.getName(),
                "Security policy updated: " + saved.getName());

        log.info("Security policy {} updated by {}", saved.getName(), admin.getUsername());
        return SecurityPolicyDTO.fromEntity(saved);
    }

    /**
     * Validate a password against the security policy.
     */
    public PasswordValidationResult validatePassword(String password, User user) {
        SecurityPolicy policy = getActivePolicy();
        List<String> errors = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            errors.add("Password cannot be empty");
            return new PasswordValidationResult(false, errors);
        }

        // Length checks
        if (password.length() < policy.getMinPasswordLength()) {
            errors.add("Password must be at least " + policy.getMinPasswordLength() + " characters");
        }
        if (password.length() > policy.getMaxPasswordLength()) {
            errors.add("Password must not exceed " + policy.getMaxPasswordLength() + " characters");
        }

        // Character type checks
        if (policy.isRequireUppercase() && !Pattern.compile("[A-Z]").matcher(password).find()) {
            errors.add("Password must contain at least one uppercase letter");
        }
        if (policy.isRequireLowercase() && !Pattern.compile("[a-z]").matcher(password).find()) {
            errors.add("Password must contain at least one lowercase letter");
        }
        if (policy.isRequireNumbers() && !Pattern.compile("[0-9]").matcher(password).find()) {
            errors.add("Password must contain at least one number");
        }
        if (policy.isRequireSpecialChars()) {
            String specialChars = Pattern.quote(policy.getSpecialCharsAllowed());
            if (!Pattern.compile("[" + specialChars + "]").matcher(password).find()) {
                errors.add("Password must contain at least one special character");
            }
        }

        // Common password check
        if (policy.isPreventCommonPasswords() && COMMON_PASSWORDS.contains(password.toLowerCase())) {
            errors.add("Password is too common. Please choose a stronger password");
        }

        // Username in password check
        if (user != null && user.getUsername() != null) {
            if (password.toLowerCase().contains(user.getUsername().toLowerCase())) {
                errors.add("Password cannot contain your username");
            }
        }

        // Password history check
        if (user != null && policy.getPasswordHistoryCount() > 0) {
            List<String> recentHashes = passwordHistoryRepository.findRecentHashesByUser(
                    user, PageRequest.of(0, policy.getPasswordHistoryCount()));
            for (String hash : recentHashes) {
                if (passwordEncoder.matches(password, hash)) {
                    errors.add("Password was used recently. Please choose a different password");
                    break;
                }
            }
        }

        return new PasswordValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Save password to history after successful change.
     */
    @Transactional
    public void savePasswordToHistory(User user, String encodedPassword) {
        PasswordHistory history = PasswordHistory.builder()
                .user(user)
                .passwordHash(encodedPassword)
                .build();
        passwordHistoryRepository.save(history);

        // Clean up old history entries
        SecurityPolicy policy = getActivePolicy();
        long count = passwordHistoryRepository.countByUser(user);
        if (count > policy.getPasswordHistoryCount()) {
            // Would need to implement cleanup of oldest entries
        }
    }

    /**
     * Check if an IP is allowed based on whitelist/blacklist.
     */
    public boolean isIpAllowed(String ipAddress) {
        SecurityPolicy policy = getActivePolicy();

        // Check blacklist first
        if (policy.getIpBlacklist() != null && !policy.getIpBlacklist().isEmpty()) {
            String[] blacklisted = policy.getIpBlacklist().split(",");
            for (String ip : blacklisted) {
                if (matchesIp(ipAddress, ip.trim())) {
                    return false;
                }
            }
        }

        // If whitelist is enabled, check it
        if (policy.isIpWhitelistEnabled() && policy.getIpWhitelist() != null) {
            String[] whitelisted = policy.getIpWhitelist().split(",");
            for (String ip : whitelisted) {
                if (matchesIp(ipAddress, ip.trim())) {
                    return true;
                }
            }
            return false; // Not in whitelist
        }

        return true; // No whitelist enabled, allow by default
    }

    private boolean matchesIp(String clientIp, String pattern) {
        // Simple matching - in production, add CIDR support
        if (pattern.endsWith("*")) {
            return clientIp.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return clientIp.equals(pattern);
    }

    /**
     * Get password requirements description.
     */
    public String getPasswordRequirements() {
        SecurityPolicy policy = getActivePolicy();
        StringBuilder sb = new StringBuilder();
        sb.append("Password must be ").append(policy.getMinPasswordLength())
          .append("-").append(policy.getMaxPasswordLength()).append(" characters");

        List<String> requirements = new ArrayList<>();
        if (policy.isRequireUppercase()) requirements.add("uppercase letter");
        if (policy.isRequireLowercase()) requirements.add("lowercase letter");
        if (policy.isRequireNumbers()) requirements.add("number");
        if (policy.isRequireSpecialChars()) requirements.add("special character");

        if (!requirements.isEmpty()) {
            sb.append(" and include at least one: ").append(String.join(", ", requirements));
        }

        return sb.toString();
    }

    private SecurityPolicy createDefaultPolicy() {
        SecurityPolicy policy = SecurityPolicy.builder()
                .name("default")
                .active(true)
                .defaultPolicy(true)
                .build();
        return securityPolicyRepository.save(policy);
    }

    /**
     * Password validation result.
     */
    public record PasswordValidationResult(boolean valid, List<String> errors) {}
}
