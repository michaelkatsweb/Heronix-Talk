package com.heronix.talk.controller;

import com.heronix.talk.model.dto.UserDTO;
import com.heronix.talk.model.enums.UserRole;
import com.heronix.talk.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for user operations.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllActiveUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id) {
        return userService.findById(id)
                .map(user -> ResponseEntity.ok(UserDTO.fromEntity(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/online")
    public ResponseEntity<List<UserDTO>> getOnlineUsers() {
        return ResponseEntity.ok(userService.getOnlineUsers());
    }

    @GetMapping("/online/count")
    public ResponseEntity<Long> getOnlineUserCount() {
        return ResponseEntity.ok(userService.getOnlineUserCount());
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<List<UserDTO>> getUsersByRole(@PathVariable UserRole role) {
        return ResponseEntity.ok(userService.getUsersByRole(role));
    }

    @GetMapping("/department/{department}")
    public ResponseEntity<List<UserDTO>> getUsersByDepartment(@PathVariable String department) {
        return ResponseEntity.ok(userService.getUsersByDepartment(department));
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserDTO>> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(userService.searchUsers(q));
    }
}
