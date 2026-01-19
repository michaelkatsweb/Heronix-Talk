package com.heronix.talk.service;

import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.enums.SyncStatus;
import com.heronix.talk.model.enums.UserRole;
import com.heronix.talk.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SIS (Student Information System) Sync Service
 *
 * Synchronizes teacher/staff accounts from the EduScheduler-Pro database
 * to Heronix-Talk, enabling users to login with their SIS credentials.
 *
 * This is a direct database sync approach - Talk connects to the SIS database
 * and pulls teacher records, including their password hashes.
 */
@Service
@Slf4j
public class SISSyncService {

    private final UserRepository userRepository;

    @Value("${heronix.sis.sync.enabled:false}")
    private boolean sisEnabled;

    @Value("${heronix.sis.db.url:}")
    private String sisDbUrl;

    @Value("${heronix.sis.db.username:sa}")
    private String sisDbUsername;

    @Value("${heronix.sis.db.password:}")
    private String sisDbPassword;

    @Value("${heronix.sis.db.driver:org.h2.Driver}")
    private String sisDbDriver;

    public SISSyncService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        if (sisEnabled && !sisDbUrl.isEmpty()) {
            log.info("SIS Sync enabled - will sync from: {}", sisDbUrl);
            // Run initial sync on startup
            syncFromSIS();
        } else {
            log.info("SIS Sync disabled or not configured");
        }
    }

    /**
     * Scheduled sync from SIS database
     * Runs every 5 minutes by default (configurable)
     */
    @Scheduled(fixedRateString = "${heronix.sis.sync.interval-seconds:300}000")
    public void scheduledSync() {
        if (sisEnabled && !sisDbUrl.isEmpty()) {
            syncFromSIS();
        }
    }

    /**
     * Manually trigger a sync from SIS
     */
    @Transactional
    public SyncResult syncFromSIS() {
        SyncResult result = new SyncResult();
        result.setStartTime(LocalDateTime.now());

        if (!sisEnabled || sisDbUrl.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("SIS sync is not enabled or configured");
            return result;
        }

        log.info("Starting SIS sync from: {}", sisDbUrl);

        try {
            // Load the database driver
            Class.forName(sisDbDriver);

            // Connect to SIS database
            try (Connection conn = DriverManager.getConnection(sisDbUrl, sisDbUsername, sisDbPassword)) {

                // Query teachers from SIS
                List<SISTeacher> teachers = fetchTeachersFromSIS(conn);
                log.info("Found {} teachers in SIS database", teachers.size());

                // Sync each teacher
                for (SISTeacher teacher : teachers) {
                    try {
                        syncTeacher(teacher);
                        result.setUsersProcessed(result.getUsersProcessed() + 1);
                    } catch (Exception e) {
                        log.error("Error syncing teacher {}: {}", teacher.getEmployeeId(), e.getMessage());
                        result.setErrors(result.getErrors() + 1);
                    }
                }

                result.setSuccess(true);
                result.setMessage(String.format("Synced %d teachers, %d errors",
                        result.getUsersProcessed(), result.getErrors()));

            }
        } catch (ClassNotFoundException e) {
            log.error("SIS database driver not found: {}", sisDbDriver);
            result.setSuccess(false);
            result.setMessage("Database driver not found: " + sisDbDriver);
        } catch (SQLException e) {
            log.error("Error connecting to SIS database: {}", e.getMessage());
            result.setSuccess(false);
            result.setMessage("Database connection error: " + e.getMessage());
        }

        result.setEndTime(LocalDateTime.now());
        log.info("SIS sync completed: {}", result.getMessage());
        return result;
    }

    /**
     * Fetch teachers from SIS database
     */
    private List<SISTeacher> fetchTeachersFromSIS(Connection conn) throws SQLException {
        List<SISTeacher> teachers = new ArrayList<>();

        // Query the teachers table in EduScheduler-Pro
        // Note: Adjust table/column names based on actual SIS schema
        String sql = """
            SELECT
                employee_id,
                first_name,
                last_name,
                email,
                department,
                password_hash,
                active,
                phone_number
            FROM teachers
            WHERE active = true
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                SISTeacher teacher = new SISTeacher();
                teacher.setEmployeeId(rs.getString("employee_id"));
                teacher.setFirstName(rs.getString("first_name"));
                teacher.setLastName(rs.getString("last_name"));
                teacher.setEmail(rs.getString("email"));
                teacher.setDepartment(rs.getString("department"));
                teacher.setPasswordHash(rs.getString("password_hash"));
                teacher.setActive(rs.getBoolean("active"));
                teacher.setPhoneNumber(rs.getString("phone_number"));
                teachers.add(teacher);
            }
        } catch (SQLException e) {
            // Table might have different structure, try alternative query
            log.warn("Primary query failed, trying alternative schema: {}", e.getMessage());
            return fetchTeachersAlternative(conn);
        }

        return teachers;
    }

    /**
     * Alternative fetch method for different SIS schema
     */
    private List<SISTeacher> fetchTeachersAlternative(Connection conn) throws SQLException {
        List<SISTeacher> teachers = new ArrayList<>();

        // Try a more generic approach - look for common table names
        String[] tableNames = {"teachers", "teacher", "staff", "users", "employees"};

        for (String tableName : tableNames) {
            try {
                // Check if table exists
                DatabaseMetaData meta = conn.getMetaData();
                ResultSet tables = meta.getTables(null, null, tableName.toUpperCase(), null);

                if (tables.next()) {
                    log.info("Found table: {}", tableName);

                    // Get column names
                    ResultSet columns = meta.getColumns(null, null, tableName.toUpperCase(), null);
                    List<String> columnNames = new ArrayList<>();
                    while (columns.next()) {
                        columnNames.add(columns.getString("COLUMN_NAME").toLowerCase());
                    }
                    log.debug("Columns in {}: {}", tableName, columnNames);

                    // Build dynamic query based on available columns
                    StringBuilder sql = new StringBuilder("SELECT * FROM " + tableName);

                    // Add active filter if column exists
                    if (columnNames.contains("active")) {
                        sql.append(" WHERE active = true");
                    }

                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(sql.toString())) {

                        while (rs.next()) {
                            SISTeacher teacher = mapResultSetToTeacher(rs, columnNames);
                            if (teacher.getEmployeeId() != null) {
                                teachers.add(teacher);
                            }
                        }
                    }

                    if (!teachers.isEmpty()) {
                        log.info("Successfully fetched {} records from {}", teachers.size(), tableName);
                        return teachers;
                    }
                }
            } catch (SQLException e) {
                log.debug("Table {} not accessible: {}", tableName, e.getMessage());
            }
        }

        return teachers;
    }

    /**
     * Map a ResultSet row to SISTeacher based on available columns
     */
    private SISTeacher mapResultSetToTeacher(ResultSet rs, List<String> columns) throws SQLException {
        SISTeacher teacher = new SISTeacher();

        // Employee ID (required)
        teacher.setEmployeeId(getStringColumn(rs, columns,
                "employee_id", "employeeid", "emp_id", "id", "teacher_id"));

        // First Name
        teacher.setFirstName(getStringColumn(rs, columns,
                "first_name", "firstname", "fname", "given_name"));

        // Last Name
        teacher.setLastName(getStringColumn(rs, columns,
                "last_name", "lastname", "lname", "surname", "family_name"));

        // Email
        teacher.setEmail(getStringColumn(rs, columns,
                "email", "email_address", "mail"));

        // Department
        teacher.setDepartment(getStringColumn(rs, columns,
                "department", "dept", "subject", "subject_area"));

        // Password Hash
        teacher.setPasswordHash(getStringColumn(rs, columns,
                "password_hash", "passwordhash", "password", "pwd_hash"));

        // Phone
        teacher.setPhoneNumber(getStringColumn(rs, columns,
                "phone_number", "phone", "phonenumber", "mobile"));

        // Active status
        teacher.setActive(getBooleanColumn(rs, columns, "active", true));

        return teacher;
    }

    private String getStringColumn(ResultSet rs, List<String> columns, String... possibleNames) {
        for (String name : possibleNames) {
            if (columns.contains(name.toLowerCase())) {
                try {
                    return rs.getString(name);
                } catch (SQLException e) {
                    // Try next name
                }
            }
        }
        return null;
    }

    private boolean getBooleanColumn(ResultSet rs, List<String> columns, String name, boolean defaultValue) {
        if (columns.contains(name.toLowerCase())) {
            try {
                return rs.getBoolean(name);
            } catch (SQLException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Sync a single teacher to the local database
     */
    @Transactional
    public User syncTeacher(SISTeacher sisTeacher) {
        if (sisTeacher.getEmployeeId() == null || sisTeacher.getEmployeeId().isBlank()) {
            throw new IllegalArgumentException("Employee ID is required");
        }

        Optional<User> existingUser = userRepository.findByEmployeeId(sisTeacher.getEmployeeId());

        if (existingUser.isPresent()) {
            // Update existing user
            User user = existingUser.get();
            updateUserFromSIS(user, sisTeacher);
            log.debug("Updated user from SIS: {}", sisTeacher.getEmployeeId());
            return userRepository.save(user);
        } else {
            // Create new user
            User newUser = createUserFromSIS(sisTeacher);
            log.info("Created new user from SIS: {}", sisTeacher.getEmployeeId());
            return userRepository.save(newUser);
        }
    }

    private void updateUserFromSIS(User user, SISTeacher sis) {
        if (sis.getFirstName() != null) user.setFirstName(sis.getFirstName());
        if (sis.getLastName() != null) user.setLastName(sis.getLastName());
        if (sis.getEmail() != null) user.setEmail(sis.getEmail());
        if (sis.getDepartment() != null) user.setDepartment(sis.getDepartment());
        if (sis.getPhoneNumber() != null) user.setPhoneNumber(sis.getPhoneNumber());

        // Update password hash if provided (allows password sync from SIS)
        if (sis.getPasswordHash() != null && !sis.getPasswordHash().isBlank()) {
            user.setPasswordHash(sis.getPasswordHash());
        }

        user.setActive(sis.isActive());
        user.setSyncStatus(SyncStatus.SYNCED);
        user.setLastSyncTime(LocalDateTime.now());
        user.setSyncSource("sis-database");
    }

    private User createUserFromSIS(SISTeacher sis) {
        return User.builder()
                .username(sis.getEmployeeId().toLowerCase())
                .employeeId(sis.getEmployeeId())
                .firstName(sis.getFirstName() != null ? sis.getFirstName() : "Unknown")
                .lastName(sis.getLastName() != null ? sis.getLastName() : "User")
                .email(sis.getEmail() != null ? sis.getEmail() : sis.getEmployeeId() + "@school.edu")
                .department(sis.getDepartment())
                .phoneNumber(sis.getPhoneNumber())
                .passwordHash(sis.getPasswordHash())
                .role(UserRole.TEACHER)
                .active(sis.isActive())
                .syncStatus(SyncStatus.SYNCED)
                .lastSyncTime(LocalDateTime.now())
                .syncSource("sis-database")
                .build();
    }

    /**
     * Check if SIS sync is configured and working
     */
    public boolean isSISAvailable() {
        if (!sisEnabled || sisDbUrl.isEmpty()) {
            return false;
        }

        try {
            Class.forName(sisDbDriver);
            try (Connection conn = DriverManager.getConnection(sisDbUrl, sisDbUsername, sisDbPassword)) {
                return conn.isValid(5);
            }
        } catch (Exception e) {
            log.debug("SIS database not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get current sync status
     */
    public SISSyncStatus getSyncStatus() {
        return new SISSyncStatus();
    }

    // ==================== Inner Classes ====================

    /**
     * DTO for teacher data from SIS
     */
    @Data
    public static class SISTeacher {
        private String employeeId;
        private String firstName;
        private String lastName;
        private String email;
        private String department;
        private String passwordHash;
        private String phoneNumber;
        private boolean active = true;
    }

    /**
     * Result of a sync operation
     */
    @Data
    public static class SyncResult {
        private boolean success;
        private String message;
        private int usersProcessed;
        private int usersCreated;
        private int usersUpdated;
        private int errors;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    /**
     * Current sync status
     */
    @Data
    public static class SISSyncStatus {
        private boolean enabled;
        private boolean sisAvailable;
        private LocalDateTime lastSyncTime;
        private int totalSyncedUsers;
    }
}
