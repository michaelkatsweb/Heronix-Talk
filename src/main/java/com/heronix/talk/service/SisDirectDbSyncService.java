package com.heronix.talk.service;

import com.heronix.talk.config.SisIntegrationProperties;
import com.heronix.talk.model.dto.ImportResultDTO;
import com.heronix.talk.model.dto.SisUserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for synchronizing users directly from the SIS database.
 * This is a fallback option when the API is not available.
 * Supports H2, MySQL, and PostgreSQL databases.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SisDirectDbSyncService {

    private final SisIntegrationProperties sisProperties;
    private final SisSyncService sisSyncService;
    private final AuditService auditService;

    /**
     * Sync users from the SIS database using direct JDBC connection.
     */
    public ImportResultDTO syncFromDatabase(DatabaseConfig dbConfig) {
        long startTime = System.currentTimeMillis();
        String source = "SIS Database: " + dbConfig.getUrl();

        try (Connection conn = getConnection(dbConfig)) {
            List<SisUserDTO> users = fetchUsersFromDatabase(conn, dbConfig);
            ImportResultDTO result = sisSyncService.processUsers(users, source);
            result.setDurationMs(System.currentTimeMillis() - startTime);
            result.setSuccess(result.getErrors() == 0);

            auditService.logAdminAction(null, "SIS_DB_SYNC",
                    String.format("Database sync completed: %d processed, %d created, %d updated, %d errors",
                            result.getTotalProcessed(), result.getCreated(),
                            result.getUpdated(), result.getErrors()));

            return result;
        } catch (SQLException e) {
            log.error("Failed to sync from SIS database", e);
            return ImportResultDTO.createError(source, "Database sync failed: " + e.getMessage());
        }
    }

    /**
     * Sync users from the SIS database using default configuration.
     */
    public ImportResultDTO syncFromDatabase() {
        SisIntegrationProperties.Database dbProps = sisProperties.getDatabase();
        if (dbProps == null || !dbProps.isEnabled()) {
            return ImportResultDTO.createError("SIS Database", "Database sync is not configured or disabled");
        }

        DatabaseConfig config = DatabaseConfig.builder()
                .url(dbProps.getUrl())
                .username(dbProps.getUsername())
                .password(dbProps.getPassword())
                .driverClassName(dbProps.getDriverClassName())
                .tableName(dbProps.getTableName())
                .build();

        return syncFromDatabase(config);
    }

    /**
     * Test database connection.
     */
    public boolean testConnection(DatabaseConfig dbConfig) {
        try (Connection conn = getConnection(dbConfig)) {
            return conn.isValid(5);
        } catch (SQLException e) {
            log.debug("Database connection test failed", e);
            return false;
        }
    }

    /**
     * Test database connection using default configuration.
     */
    public boolean testConnection() {
        SisIntegrationProperties.Database dbProps = sisProperties.getDatabase();
        if (dbProps == null || !dbProps.isEnabled()) {
            return false;
        }

        DatabaseConfig config = DatabaseConfig.builder()
                .url(dbProps.getUrl())
                .username(dbProps.getUsername())
                .password(dbProps.getPassword())
                .driverClassName(dbProps.getDriverClassName())
                .build();

        return testConnection(config);
    }

    private Connection getConnection(DatabaseConfig config) throws SQLException {
        if (config.getDriverClassName() != null && !config.getDriverClassName().isBlank()) {
            try {
                Class.forName(config.getDriverClassName());
            } catch (ClassNotFoundException e) {
                throw new SQLException("JDBC driver not found: " + config.getDriverClassName(), e);
            }
        }
        return DriverManager.getConnection(config.getUrl(), config.getUsername(), config.getPassword());
    }

    private List<SisUserDTO> fetchUsersFromDatabase(Connection conn, DatabaseConfig config) throws SQLException {
        List<SisUserDTO> users = new ArrayList<>();
        String tableName = config.getTableName() != null ? config.getTableName() : "teachers";

        // Build a flexible query that handles various column naming conventions
        String sql = buildSelectQuery(conn, tableName);
        log.debug("Executing SIS database query: {}", sql);

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData meta = rs.getMetaData();
            ColumnMapping mapping = detectColumnMapping(meta);

            while (rs.next()) {
                SisUserDTO user = mapResultSetToUser(rs, mapping);
                if (user != null) {
                    users.add(user);
                }
            }
        }

        log.info("Fetched {} users from SIS database", users.size());
        return users;
    }

    private String buildSelectQuery(Connection conn, String tableName) throws SQLException {
        // Try to detect if the table exists and build appropriate query
        DatabaseMetaData dbMeta = conn.getMetaData();

        // Check for common table names
        String[] tableNames = {tableName, "teachers", "staff", "employees", "users", "teacher"};
        String actualTable = null;

        for (String name : tableNames) {
            try (ResultSet tables = dbMeta.getTables(null, null, name, new String[]{"TABLE"})) {
                if (tables.next()) {
                    actualTable = name;
                    break;
                }
            }
            // Try uppercase for case-sensitive databases
            try (ResultSet tables = dbMeta.getTables(null, null, name.toUpperCase(), new String[]{"TABLE"})) {
                if (tables.next()) {
                    actualTable = name.toUpperCase();
                    break;
                }
            }
        }

        if (actualTable == null) {
            actualTable = tableName; // Fall back to configured name
        }

        return "SELECT * FROM " + actualTable + " WHERE 1=1";
    }

    private ColumnMapping detectColumnMapping(ResultSetMetaData meta) throws SQLException {
        ColumnMapping mapping = new ColumnMapping();
        int columnCount = meta.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            String colName = meta.getColumnName(i).toLowerCase();

            // Map employee ID
            if (colName.contains("employee") && colName.contains("id") ||
                colName.equals("emp_id") || colName.equals("empid") ||
                colName.equals("teacher_id") || colName.equals("teacherid") ||
                colName.equals("staff_id") || colName.equals("staffid") ||
                (colName.equals("id") && mapping.employeeIdCol == null)) {
                mapping.employeeIdCol = meta.getColumnName(i);
            }
            // Map username
            else if (colName.equals("username") || colName.equals("user_name") ||
                     colName.equals("login") || colName.equals("loginname")) {
                mapping.usernameCol = meta.getColumnName(i);
            }
            // Map first name
            else if (colName.equals("firstname") || colName.equals("first_name") ||
                     colName.equals("fname") || colName.equals("givenname")) {
                mapping.firstNameCol = meta.getColumnName(i);
            }
            // Map last name
            else if (colName.equals("lastname") || colName.equals("last_name") ||
                     colName.equals("lname") || colName.equals("surname") ||
                     colName.equals("familyname")) {
                mapping.lastNameCol = meta.getColumnName(i);
            }
            // Map full name
            else if (colName.equals("fullname") || colName.equals("full_name") ||
                     colName.equals("name") || colName.equals("displayname")) {
                mapping.fullNameCol = meta.getColumnName(i);
            }
            // Map email
            else if (colName.equals("email") || colName.equals("emailaddress") ||
                     colName.equals("email_address") || colName.equals("mail")) {
                mapping.emailCol = meta.getColumnName(i);
            }
            // Map department
            else if (colName.equals("department") || colName.equals("dept") ||
                     colName.equals("departmentname")) {
                mapping.departmentCol = meta.getColumnName(i);
            }
            // Map subject (as alternative to department)
            else if (colName.equals("subject") || colName.equals("subjects") ||
                     colName.equals("teachingsubject")) {
                mapping.subjectCol = meta.getColumnName(i);
            }
            // Map phone
            else if (colName.equals("phone") || colName.equals("phonenumber") ||
                     colName.equals("phone_number") || colName.equals("telephone") ||
                     colName.equals("mobile")) {
                mapping.phoneCol = meta.getColumnName(i);
            }
            // Map role
            else if (colName.equals("role") || colName.equals("rolename") ||
                     colName.equals("position") || colName.equals("title") ||
                     colName.equals("jobtitle")) {
                mapping.roleCol = meta.getColumnName(i);
            }
            // Map active status
            else if (colName.equals("active") || colName.equals("isactive") ||
                     colName.equals("is_active") || colName.equals("enabled") ||
                     colName.equals("status")) {
                mapping.activeCol = meta.getColumnName(i);
            }
        }

        log.debug("Detected column mapping: {}", mapping);
        return mapping;
    }

    private SisUserDTO mapResultSetToUser(ResultSet rs, ColumnMapping mapping) {
        try {
            SisUserDTO.SisUserDTOBuilder builder = SisUserDTO.builder();

            if (mapping.employeeIdCol != null) {
                builder.employeeId(rs.getString(mapping.employeeIdCol));
            }
            if (mapping.usernameCol != null) {
                builder.username(rs.getString(mapping.usernameCol));
            }
            if (mapping.firstNameCol != null) {
                builder.firstName(rs.getString(mapping.firstNameCol));
            }
            if (mapping.lastNameCol != null) {
                builder.lastName(rs.getString(mapping.lastNameCol));
            }
            if (mapping.fullNameCol != null) {
                builder.fullName(rs.getString(mapping.fullNameCol));
            }
            if (mapping.emailCol != null) {
                builder.email(rs.getString(mapping.emailCol));
            }
            if (mapping.departmentCol != null) {
                builder.department(rs.getString(mapping.departmentCol));
            }
            if (mapping.subjectCol != null) {
                builder.subject(rs.getString(mapping.subjectCol));
            }
            if (mapping.phoneCol != null) {
                builder.phoneNumber(rs.getString(mapping.phoneCol));
            }
            if (mapping.roleCol != null) {
                builder.role(rs.getString(mapping.roleCol));
            }
            if (mapping.activeCol != null) {
                Object activeVal = rs.getObject(mapping.activeCol);
                builder.active(parseActiveStatus(activeVal));
            } else {
                builder.active(true); // Default to active
            }

            return builder.build();
        } catch (SQLException e) {
            log.warn("Error mapping database row to user", e);
            return null;
        }
    }

    private boolean parseActiveStatus(Object value) {
        if (value == null) return true;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        String str = value.toString().toLowerCase();
        return str.equals("true") || str.equals("1") || str.equals("yes") ||
               str.equals("active") || str.equals("y");
    }

    /**
     * Get database sync status.
     */
    public DbSyncStatusDTO getStatus() {
        SisIntegrationProperties.Database dbProps = sisProperties.getDatabase();
        boolean configured = dbProps != null && dbProps.isEnabled();

        return DbSyncStatusDTO.builder()
                .enabled(configured)
                .url(configured ? dbProps.getUrl() : null)
                .tableName(configured ? dbProps.getTableName() : null)
                .connectionOk(configured && testConnection())
                .build();
    }

    // Inner classes for configuration and mapping

    @lombok.Data
    @lombok.Builder
    public static class DatabaseConfig {
        private String url;
        private String username;
        private String password;
        private String driverClassName;
        private String tableName;
    }

    @lombok.Data
    @lombok.Builder
    public static class DbSyncStatusDTO {
        private boolean enabled;
        private String url;
        private String tableName;
        private boolean connectionOk;
    }

    private static class ColumnMapping {
        String employeeIdCol;
        String usernameCol;
        String firstNameCol;
        String lastNameCol;
        String fullNameCol;
        String emailCol;
        String departmentCol;
        String subjectCol;
        String phoneCol;
        String roleCol;
        String activeCol;

        @Override
        public String toString() {
            return String.format("ColumnMapping[empId=%s, username=%s, firstName=%s, lastName=%s, fullName=%s, email=%s]",
                    employeeIdCol, usernameCol, firstNameCol, lastNameCol, fullNameCol, emailCol);
        }
    }
}
