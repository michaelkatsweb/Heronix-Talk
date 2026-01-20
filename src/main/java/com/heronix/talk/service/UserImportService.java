package com.heronix.talk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heronix.talk.config.SisIntegrationProperties;
import com.heronix.talk.model.dto.ImportResultDTO;
import com.heronix.talk.model.dto.SisUserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for importing users from CSV or JSON files.
 * Provides a manual import option when SIS API is not available.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserImportService {

    private final SisIntegrationProperties sisProperties;
    private final SisSyncService sisSyncService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    private static final String[] CSV_HEADERS = {
            "employeeId", "username", "firstName", "lastName", "email",
            "department", "phoneNumber", "role", "active"
    };

    /**
     * Import users from an uploaded file (CSV or JSON).
     */
    public ImportResultDTO importFromFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return ImportResultDTO.createError("upload", "No filename provided");
        }

        String source = "File upload: " + filename;
        long startTime = System.currentTimeMillis();

        try {
            List<SisUserDTO> users;
            if (filename.toLowerCase().endsWith(".json")) {
                users = parseJsonFile(file);
            } else if (filename.toLowerCase().endsWith(".csv")) {
                users = parseCsvFile(file);
            } else {
                return ImportResultDTO.createError(source, "Unsupported file format. Use CSV or JSON.");
            }

            ImportResultDTO result = sisSyncService.processUsers(users, source);
            result.setDurationMs(System.currentTimeMillis() - startTime);
            result.setSuccess(result.getErrors() == 0);

            auditService.logAdminAction(null, "USER_IMPORT",
                    String.format("File import completed (%s): %d processed, %d created, %d updated, %d errors",
                            filename, result.getTotalProcessed(), result.getCreated(),
                            result.getUpdated(), result.getErrors()));

            return result;
        } catch (Exception e) {
            log.error("Failed to import users from file: {}", filename, e);
            return ImportResultDTO.createError(source, "Import failed: " + e.getMessage());
        }
    }

    /**
     * Import users from a file path on the server.
     */
    public ImportResultDTO importFromPath(String filePath) {
        Path path = Paths.get(filePath);
        String source = "File: " + filePath;

        if (!Files.exists(path)) {
            return ImportResultDTO.createError(source, "File not found: " + filePath);
        }

        long startTime = System.currentTimeMillis();

        try {
            List<SisUserDTO> users;
            if (filePath.toLowerCase().endsWith(".json")) {
                users = parseJsonFile(path);
            } else if (filePath.toLowerCase().endsWith(".csv")) {
                users = parseCsvFile(path);
            } else {
                return ImportResultDTO.createError(source, "Unsupported file format. Use CSV or JSON.");
            }

            ImportResultDTO result = sisSyncService.processUsers(users, source);
            result.setDurationMs(System.currentTimeMillis() - startTime);
            result.setSuccess(result.getErrors() == 0);

            // Move processed file to processed directory
            moveToProcessed(path);

            auditService.logAdminAction(null, "USER_IMPORT",
                    String.format("File import completed (%s): %d processed, %d created, %d updated, %d errors",
                            path.getFileName(), result.getTotalProcessed(), result.getCreated(),
                            result.getUpdated(), result.getErrors()));

            return result;
        } catch (Exception e) {
            log.error("Failed to import users from path: {}", filePath, e);
            return ImportResultDTO.createError(source, "Import failed: " + e.getMessage());
        }
    }

    /**
     * Parse a JSON file containing user data.
     */
    private List<SisUserDTO> parseJsonFile(MultipartFile file) throws IOException {
        return objectMapper.readValue(file.getInputStream(), new TypeReference<List<SisUserDTO>>() {});
    }

    /**
     * Parse a JSON file from a path.
     */
    private List<SisUserDTO> parseJsonFile(Path path) throws IOException {
        return objectMapper.readValue(path.toFile(), new TypeReference<List<SisUserDTO>>() {});
    }

    /**
     * Parse a CSV file containing user data.
     * Expected format: employeeId,username,firstName,lastName,email,department,phoneNumber,role,active
     */
    private List<SisUserDTO> parseCsvFile(MultipartFile file) throws IOException {
        List<SisUserDTO> users = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            parseCsvContent(reader, users);
        }

        return users;
    }

    /**
     * Parse a CSV file from a path.
     */
    private List<SisUserDTO> parseCsvFile(Path path) throws IOException {
        List<SisUserDTO> users = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            parseCsvContent(reader, users);
        }

        return users;
    }

    /**
     * Parse CSV content from a reader.
     */
    private void parseCsvContent(BufferedReader reader, List<SisUserDTO> users) throws IOException {
        String line;
        int lineNumber = 0;
        String[] headers = null;

        while ((line = reader.readLine()) != null) {
            lineNumber++;
            line = line.trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue; // Skip empty lines and comments
            }

            String[] values = parseCsvLine(line);

            if (lineNumber == 1 && looksLikeHeader(values)) {
                headers = values;
                continue;
            }

            try {
                SisUserDTO user = mapCsvToUser(values, headers);
                if (user.isValid()) {
                    users.add(user);
                } else {
                    log.warn("Skipped invalid CSV row at line {}: {}", lineNumber, line);
                }
            } catch (Exception e) {
                log.warn("Failed to parse CSV row at line {}: {}", lineNumber, e.getMessage());
            }
        }
    }

    /**
     * Parse a CSV line, handling quoted values.
     */
    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());

        return values.toArray(new String[0]);
    }

    /**
     * Check if a row looks like a header row.
     */
    private boolean looksLikeHeader(String[] values) {
        if (values.length == 0) return false;

        String first = values[0].toLowerCase();
        return first.equals("employeeid") || first.equals("employee_id") ||
                first.equals("id") || first.equals("username");
    }

    /**
     * Map CSV values to a SisUserDTO.
     */
    private SisUserDTO mapCsvToUser(String[] values, String[] headers) {
        SisUserDTO.SisUserDTOBuilder builder = SisUserDTO.builder();

        if (headers != null) {
            // Map by header names
            for (int i = 0; i < headers.length && i < values.length; i++) {
                String header = headers[i].toLowerCase().replace("_", "").replace("-", "");
                String value = values[i].trim();

                if (value.isEmpty()) continue;

                switch (header) {
                    case "employeeid", "empid", "id" -> builder.employeeId(value);
                    case "username", "user" -> builder.username(value);
                    case "firstname", "first" -> builder.firstName(value);
                    case "lastname", "last" -> builder.lastName(value);
                    case "email" -> builder.email(value);
                    case "department", "dept" -> builder.department(value);
                    case "phone", "phonenumber" -> builder.phoneNumber(value);
                    case "role" -> builder.role(value);
                    case "active" -> builder.active(parseBoolean(value));
                }
            }
        } else {
            // Map by position (default column order)
            if (values.length > 0) builder.employeeId(values[0]);
            if (values.length > 1) builder.username(values[1]);
            if (values.length > 2) builder.firstName(values[2]);
            if (values.length > 3) builder.lastName(values[3]);
            if (values.length > 4) builder.email(values[4]);
            if (values.length > 5) builder.department(values[5]);
            if (values.length > 6) builder.phoneNumber(values[6]);
            if (values.length > 7) builder.role(values[7]);
            if (values.length > 8) builder.active(parseBoolean(values[8]));
        }

        return builder.build();
    }

    /**
     * Parse a boolean value from various string representations.
     */
    private boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) return true;
        String lower = value.toLowerCase().trim();
        return lower.equals("true") || lower.equals("yes") || lower.equals("1") || lower.equals("active");
    }

    /**
     * Move a processed file to the processed directory.
     */
    private void moveToProcessed(Path sourcePath) {
        try {
            Path processedDir = Paths.get(sisProperties.getImportConfig().getProcessedDirectory());
            Files.createDirectories(processedDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String newFileName = timestamp + "_" + sourcePath.getFileName();
            Path targetPath = processedDir.resolve(newFileName);

            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Moved processed file to: {}", targetPath);
        } catch (IOException e) {
            log.warn("Failed to move processed file: {}", e.getMessage());
        }
    }

    /**
     * List files available for import in the import directory.
     */
    public List<String> listImportFiles() {
        List<String> files = new ArrayList<>();
        Path importDir = Paths.get(sisProperties.getImportConfig().getDirectory());

        if (!Files.exists(importDir)) {
            return files;
        }

        try (var stream = Files.list(importDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".csv") || name.endsWith(".json");
                    })
                    .forEach(p -> files.add(p.toString()));
        } catch (IOException e) {
            log.error("Failed to list import files", e);
        }

        return files;
    }

    /**
     * Get a sample CSV template.
     */
    public String getCsvTemplate() {
        return """
                # User Import CSV Template
                # Fields: employeeId, username, firstName, lastName, email, department, phoneNumber, role, active
                # Role values: ADMIN, PRINCIPAL, TEACHER, STAFF, COUNSELOR, DEPARTMENT_HEAD
                # Active values: true/false, yes/no, 1/0

                employeeId,username,firstName,lastName,email,department,phoneNumber,role,active
                EMP001,jsmith,John,Smith,jsmith@school.edu,Math,555-0100,TEACHER,true
                EMP002,mjones,Mary,Jones,mjones@school.edu,English,555-0101,TEACHER,true
                EMP003,admin,Admin,User,admin@school.edu,Administration,555-0102,ADMIN,true
                """;
    }

    /**
     * Get a sample JSON template.
     */
    public String getJsonTemplate() {
        try {
            List<SisUserDTO> sample = List.of(
                    SisUserDTO.builder()
                            .employeeId("EMP001")
                            .username("jsmith")
                            .firstName("John")
                            .lastName("Smith")
                            .email("jsmith@school.edu")
                            .department("Math")
                            .phoneNumber("555-0100")
                            .role("TEACHER")
                            .active(true)
                            .build(),
                    SisUserDTO.builder()
                            .employeeId("EMP002")
                            .username("mjones")
                            .firstName("Mary")
                            .lastName("Jones")
                            .email("mjones@school.edu")
                            .department("English")
                            .phoneNumber("555-0101")
                            .role("TEACHER")
                            .active(true)
                            .build()
            );
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sample);
        } catch (Exception e) {
            return "[]";
        }
    }
}
