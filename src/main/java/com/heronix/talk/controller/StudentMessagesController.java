package com.heronix.talk.controller;

import com.heronix.talk.model.domain.Channel;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.ChannelDTO;
import com.heronix.talk.model.dto.MessageDTO;
import com.heronix.talk.model.dto.SendMessageRequest;
import com.heronix.talk.model.dto.UserDTO;
import com.heronix.talk.model.enums.MessageType;
import com.heronix.talk.model.enums.UserRole;
import com.heronix.talk.service.AuthenticationService;
import com.heronix.talk.service.ChannelService;
import com.heronix.talk.service.MessageService;
import com.heronix.talk.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Student Messages Controller
 *
 * Handles messaging between students and teachers/staff.
 * Students can:
 * - Receive messages from teachers and administrators
 * - Send messages to their teachers
 * - Reply to teacher messages
 * - View announcements and alerts
 *
 * @author Heronix Team
 * @version 1.0.0
 * @since January 2026
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class StudentMessagesController {

    private final AuthenticationService authenticationService;
    private final MessageService messageService;
    private final ChannelService channelService;
    private final UserService userService;

    // ========================================================================
    // STUDENT AUTHENTICATION
    // ========================================================================

    /**
     * Student portal connection endpoint
     * Creates or validates a student session for messaging
     */
    @PostMapping("/auth/student-connect")
    public ResponseEntity<?> studentConnect(@RequestBody Map<String, Object> request) {
        log.info("Student portal connection request received");

        try {
            String studentId = String.valueOf(request.get("studentId"));
            String studentName = (String) request.get("studentName");
            String portalType = (String) request.get("portalType");

            if (studentId == null || studentName == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing studentId or studentName"));
            }

            // Find or create student user
            User studentUser = findOrCreateStudentUser(studentId, studentName);

            // Generate session token
            String token = generateStudentToken(studentUser);

            log.info("Student {} connected successfully", studentName);

            return ResponseEntity.ok(Map.of(
                "token", token,
                "userId", studentUser.getId(),
                "studentId", studentId,
                "name", studentUser.getFirstName() + " " + studentUser.getLastName(),
                "role", "STUDENT",
                "connected", true
            ));

        } catch (Exception e) {
            log.error("Student connection failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Connection failed: " + e.getMessage()));
        }
    }

    // ========================================================================
    // GET MESSAGES FOR STUDENT
    // ========================================================================

    /**
     * Get all messages for a student
     * Returns messages sent to the student by teachers/staff
     */
    @GetMapping("/messages/student/{studentId}")
    public ResponseEntity<?> getMessagesForStudent(
            @PathVariable String studentId,
            @RequestParam(required = false) String category,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {

        log.info("Fetching messages for student: {}", studentId);

        try {
            // Find student user
            User student = findStudentByExternalId(studentId);
            if (student == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            // Get all DM channels where student is a member
            List<ChannelDTO> dmChannels = channelService.getUserDirectMessages(student.getId());

            // Collect messages from all DM channels
            List<MessageDTO> allMessages = new ArrayList<>();
            for (ChannelDTO channel : dmChannels) {
                List<MessageDTO> channelMessages = messageService.getChannelMessages(channel.getId(), 0, 100);
                for (MessageDTO dto : channelMessages) {
                    // Determine category based on sender - need to look up sender role
                    String senderCategory = determineCategoryFromSenderId(dto.getSenderId());
                    dto.setCategory(senderCategory);
                    // Students can reply to messages from teachers/staff
                    dto.setCanReply(!dto.getSenderId().equals(student.getId()));
                    allMessages.add(dto);
                }
            }

            // Filter by category if specified
            if (category != null && !category.isEmpty()) {
                allMessages = allMessages.stream()
                    .filter(m -> category.equalsIgnoreCase(m.getCategory()))
                    .collect(Collectors.toList());
            }

            // Sort by timestamp descending (newest first)
            allMessages.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

            return ResponseEntity.ok(allMessages);

        } catch (Exception e) {
            log.error("Error fetching messages for student {}", studentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ========================================================================
    // SEND MESSAGE FROM STUDENT
    // ========================================================================

    /**
     * Send a new message from student to teacher
     */
    @PostMapping("/messages/from-student")
    public ResponseEntity<?> sendMessageFromStudent(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {

        log.info("Student sending message to teacher");

        try {
            String teacherId = String.valueOf(request.get("teacherId"));
            String subject = (String) request.get("subject");
            String content = (String) request.get("content");
            String studentId = String.valueOf(request.get("studentId"));
            String studentName = (String) request.get("studentName");

            if (teacherId == null || content == null || studentId == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing required fields"));
            }

            // Find or create student user
            User student = findOrCreateStudentUser(studentId, studentName);

            // Find teacher - first try by ID, then by employee ID
            User teacher = userService.findById(Long.parseLong(teacherId)).orElse(null);
            if (teacher == null) {
                // Try finding by employee ID
                teacher = userService.findByEmployeeId(teacherId).orElse(null);
            }

            if (teacher == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Teacher not found: " + teacherId));
            }

            // Get or create DM channel between student and teacher
            Channel dmChannel = channelService.getOrCreateDirectMessage(student, teacher);

            // Format message content with subject
            String fullContent = subject != null && !subject.isEmpty()
                ? "**" + subject + "**\n\n" + content
                : content;

            // Create and send message
            SendMessageRequest msgRequest = new SendMessageRequest();
            msgRequest.setChannelId(dmChannel.getId());
            msgRequest.setContent(fullContent);
            msgRequest.setMessageType(MessageType.TEXT);

            Message sentMessage = messageService.sendMessage(msgRequest, student, dmChannel);

            log.info("Student {} sent message to teacher {}", studentName, teacher.getUsername());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", sentMessage.getId(),
                "message", "Message sent successfully"
            ));

        } catch (Exception e) {
            log.error("Error sending message from student", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to send message: " + e.getMessage()));
        }
    }

    /**
     * Reply to a teacher message
     */
    @PostMapping("/messages/student-reply")
    public ResponseEntity<?> studentReply(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {

        log.info("Student replying to message");

        try {
            String teacherId = String.valueOf(request.get("teacherId"));
            String replyToMessageId = String.valueOf(request.get("replyToMessageId"));
            String content = (String) request.get("content");
            String studentId = String.valueOf(request.get("studentId"));
            String studentName = (String) request.get("studentName");

            if (teacherId == null || content == null || studentId == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing required fields"));
            }

            // Find student and teacher
            User student = findOrCreateStudentUser(studentId, studentName);
            User teacher = userService.findById(Long.parseLong(teacherId)).orElse(null);
            if (teacher == null) {
                teacher = userService.findByEmployeeId(teacherId).orElse(null);
            }

            if (teacher == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Teacher not found"));
            }

            // Get DM channel
            Channel dmChannel = channelService.getOrCreateDirectMessage(student, teacher);

            // Create reply message
            SendMessageRequest msgRequest = new SendMessageRequest();
            msgRequest.setChannelId(dmChannel.getId());
            msgRequest.setContent(content);
            msgRequest.setMessageType(MessageType.REPLY);

            // Set reply reference if we have the original message ID
            if (replyToMessageId != null && !replyToMessageId.equals("null")) {
                try {
                    msgRequest.setReplyToId(Long.parseLong(replyToMessageId));
                } catch (NumberFormatException ignored) {}
            }

            Message sentMessage = messageService.sendMessage(msgRequest, student, dmChannel);

            log.info("Student {} replied to teacher {}", studentName, teacher.getUsername());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", sentMessage.getId(),
                "message", "Reply sent successfully"
            ));

        } catch (Exception e) {
            log.error("Error sending student reply", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to send reply: " + e.getMessage()));
        }
    }

    // ========================================================================
    // TEACHER CONTACTS
    // ========================================================================

    /**
     * Get list of teachers a student can message
     * In a real implementation, this would filter based on the student's enrolled classes
     */
    @GetMapping("/users/teachers-for-student/{studentId}")
    public ResponseEntity<?> getTeachersForStudent(
            @PathVariable String studentId,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {

        log.info("Fetching teachers for student: {}", studentId);

        try {
            // Get all users who are teachers, counselors, or admins
            List<UserDTO> allUsers = userService.getAllActiveUsers();

            List<Map<String, Object>> teachers = allUsers.stream()
                .filter(u -> "Teacher".equals(u.getRole()) ||
                            "Counselor".equals(u.getRole()) ||
                            "Principal".equals(u.getRole()) ||
                            "Department Head".equals(u.getRole()))
                .map(t -> {
                    Map<String, Object> teacher = new HashMap<>();
                    teacher.put("teacherId", t.getId().toString());
                    teacher.put("name", t.getFirstName() + " " + t.getLastName());
                    teacher.put("department", t.getDepartment() != null ? t.getDepartment() : "General");
                    teacher.put("subjects", ""); // Would come from SIS integration
                    teacher.put("online", t.isOnline());
                    return teacher;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(teachers);

        } catch (Exception e) {
            log.error("Error fetching teachers for student", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ========================================================================
    // ACADEMIC EVENTS (Placeholder - would integrate with SIS)
    // ========================================================================

    /**
     * Get academic events for a student
     * In production, this would pull from Heronix-SIS
     */
    @GetMapping("/events/student/{studentId}")
    public ResponseEntity<?> getEventsForStudent(
            @PathVariable String studentId,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {

        log.info("Fetching events for student: {}", studentId);

        // Return empty list - events would come from SIS integration
        // The student portal falls back to demo data when this returns empty
        return ResponseEntity.ok(Collections.emptyList());
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Find or create a student user for messaging
     */
    private User findOrCreateStudentUser(String studentId, String studentName) {
        // Try to find by employee ID (we use this for student ID too)
        Optional<User> existingStudent = userService.findByEmployeeId(studentId);

        if (existingStudent.isPresent()) {
            return existingStudent.get();
        }

        // Create new student user
        User student = new User();
        student.setEmployeeId(studentId);
        student.setUsername("student_" + studentId);
        student.setRole(UserRole.STUDENT);
        student.setActive(true);

        // Parse name
        if (studentName != null && !studentName.isEmpty()) {
            String[] nameParts = studentName.split(" ", 2);
            student.setFirstName(nameParts[0]);
            student.setLastName(nameParts.length > 1 ? nameParts[1] : "");
        } else {
            student.setFirstName("Student");
            student.setLastName(studentId);
        }

        student.setEmail("student" + studentId + "@school.edu");
        student.setPasswordHash(""); // Students don't login directly to Talk

        student = userService.createUser(student);
        log.info("Created new student user: {}", student.getUsername());
        return student;
    }

    /**
     * Find student by external ID
     */
    private User findStudentByExternalId(String studentId) {
        return userService.findByEmployeeId(studentId).orElse(null);
    }

    /**
     * Generate a session token for student
     */
    private String generateStudentToken(User student) {
        // Simple token for student sessions
        return "student-" + student.getId() + "-" + System.currentTimeMillis();
    }

    /**
     * Determine message category based on sender ID
     * Looks up sender role and returns appropriate category
     */
    private String determineCategoryFromSenderId(Long senderId) {
        if (senderId == null) {
            return "SYSTEM";
        }

        Optional<User> sender = userService.findById(senderId);
        if (sender.isEmpty() || sender.get().getRole() == null) {
            return "SYSTEM";
        }

        return switch (sender.get().getRole()) {
            case TEACHER -> "ACADEMIC";
            case PRINCIPAL, ADMIN -> "ANNOUNCEMENT";
            case COUNSELOR -> "PERSONAL";
            case DEPARTMENT_HEAD -> "ACADEMIC";
            case STAFF -> "SYSTEM";
            case STUDENT -> "PERSONAL";
        };
    }
}
