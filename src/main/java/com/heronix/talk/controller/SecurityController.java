package com.heronix.talk.controller;

import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.SecurityPolicyDTO;
import com.heronix.talk.model.dto.UpdateSecurityPolicyRequest;
import com.heronix.talk.service.AuthenticationService;
import com.heronix.talk.service.SecurityPolicyService;
import com.heronix.talk.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for security policy management.
 */
@RestController
@RequestMapping("/api/admin/security")
@RequiredArgsConstructor
@Slf4j
public class SecurityController {

    private final SecurityPolicyService securityPolicyService;
    private final AuthenticationService authenticationService;
    private final UserRoleService userRoleService;

    @GetMapping("/policy")
    public ResponseEntity<SecurityPolicyDTO> getActivePolicy(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withSecurityAdmin(sessionToken, user -> {
            return ResponseEntity.ok(securityPolicyService.getActivePolicyDTO());
        });
    }

    @GetMapping("/policies")
    public ResponseEntity<List<SecurityPolicyDTO>> getAllPolicies(
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withSecurityAdmin(sessionToken, user -> {
            return ResponseEntity.ok(securityPolicyService.getAllPolicies());
        });
    }

    @GetMapping("/policy/{id}")
    public ResponseEntity<SecurityPolicyDTO> getPolicyById(
            @PathVariable Long id,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withSecurityAdmin(sessionToken, user -> {
            return securityPolicyService.getPolicyById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        });
    }

    @PutMapping("/policy/{id}")
    public ResponseEntity<SecurityPolicyDTO> updatePolicy(
            @PathVariable Long id,
            @RequestBody UpdateSecurityPolicyRequest request,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withSecurityAdmin(sessionToken, user -> {
            SecurityPolicyDTO policy = securityPolicyService.updatePolicy(id, request, user);
            return ResponseEntity.ok(policy);
        });
    }

    @GetMapping("/password-requirements")
    public ResponseEntity<Map<String, Object>> getPasswordRequirements() {
        SecurityPolicyDTO policy = securityPolicyService.getActivePolicyDTO();
        return ResponseEntity.ok(Map.of(
                "minLength", policy.getMinPasswordLength(),
                "maxLength", policy.getMaxPasswordLength(),
                "requireUppercase", policy.isRequireUppercase(),
                "requireLowercase", policy.isRequireLowercase(),
                "requireNumbers", policy.isRequireNumbers(),
                "requireSpecialChars", policy.isRequireSpecialChars(),
                "specialCharsAllowed", policy.getSpecialCharsAllowed(),
                "description", securityPolicyService.getPasswordRequirements()
        ));
    }

    @PostMapping("/validate-password")
    public ResponseEntity<Map<String, Object>> validatePassword(
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    String password = body.get("password");
                    var result = securityPolicyService.validatePassword(password, user);
                    return ResponseEntity.ok(Map.of(
                            "valid", result.valid(),
                            "errors", result.errors()
                    ));
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/check-ip")
    public ResponseEntity<Map<String, Object>> checkIpAllowed(
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return withSecurityAdmin(sessionToken, user -> {
            String ip = body.get("ip");
            boolean allowed = securityPolicyService.isIpAllowed(ip);
            return ResponseEntity.ok(Map.of(
                    "ip", ip,
                    "allowed", allowed
            ));
        });
    }

    // ==================== Helper Methods ====================

    private <T> ResponseEntity<T> withSecurityAdmin(String sessionToken, SecurityOperation<T> operation) {
        return authenticationService.getUserFromSession(sessionToken)
                .filter(user -> userRoleService.hasPermission(user, "MANAGE_SECURITY_POLICY") ||
                               "ADMIN".equalsIgnoreCase(user.getRole()))
                .map(operation::execute)
                .orElse(ResponseEntity.status(403).build());
    }

    @FunctionalInterface
    private interface SecurityOperation<T> {
        ResponseEntity<T> execute(User user);
    }
}
