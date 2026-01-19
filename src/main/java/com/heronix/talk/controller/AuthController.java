package com.heronix.talk.controller;

import com.heronix.talk.model.dto.AuthRequest;
import com.heronix.talk.model.dto.AuthResponse;
import com.heronix.talk.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication operations.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request,
                                               HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthResponse response = authenticationService.authenticate(request, ipAddress, userAgent);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(401).body(response);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("X-Session-Token") String sessionToken) {
        authenticationService.logout(sessionToken);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/validate")
    public ResponseEntity<Boolean> validateSession(@RequestHeader("X-Session-Token") String sessionToken) {
        boolean valid = authenticationService.validateSession(sessionToken);
        return ResponseEntity.ok(valid);
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAllSessions(@RequestHeader("X-Session-Token") String sessionToken) {
        authenticationService.getUserFromSession(sessionToken).ifPresent(user ->
                authenticationService.logoutAllSessions(user.getId()));
        return ResponseEntity.ok().build();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
