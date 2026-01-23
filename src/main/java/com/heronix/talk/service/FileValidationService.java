package com.heronix.talk.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for validating uploaded files.
 * Performs content-type verification, magic byte checking, and extension validation.
 */
@Service
@Slf4j
public class FileValidationService {

    @Value("${heronix.upload.max-file-size-mb:25}")
    private int maxFileSizeMb;

    @Value("${heronix.upload.allowed-extensions:jpg,jpeg,png,gif,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,zip}")
    private String allowedExtensions;

    // Map of file extensions to their expected magic bytes (file signatures)
    private static final Map<String, byte[][]> MAGIC_BYTES = new HashMap<>();

    static {
        // Images
        MAGIC_BYTES.put("jpg", new byte[][]{{(byte)0xFF, (byte)0xD8, (byte)0xFF}});
        MAGIC_BYTES.put("jpeg", new byte[][]{{(byte)0xFF, (byte)0xD8, (byte)0xFF}});
        MAGIC_BYTES.put("png", new byte[][]{{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}});
        MAGIC_BYTES.put("gif", new byte[][]{
            {0x47, 0x49, 0x46, 0x38, 0x37, 0x61}, // GIF87a
            {0x47, 0x49, 0x46, 0x38, 0x39, 0x61}  // GIF89a
        });

        // Documents
        MAGIC_BYTES.put("pdf", new byte[][]{{0x25, 0x50, 0x44, 0x46}}); // %PDF
        MAGIC_BYTES.put("doc", new byte[][]{{(byte)0xD0, (byte)0xCF, 0x11, (byte)0xE0}}); // OLE compound
        MAGIC_BYTES.put("docx", new byte[][]{{0x50, 0x4B, 0x03, 0x04}}); // ZIP (OOXML)
        MAGIC_BYTES.put("xls", new byte[][]{{(byte)0xD0, (byte)0xCF, 0x11, (byte)0xE0}});
        MAGIC_BYTES.put("xlsx", new byte[][]{{0x50, 0x4B, 0x03, 0x04}});
        MAGIC_BYTES.put("ppt", new byte[][]{{(byte)0xD0, (byte)0xCF, 0x11, (byte)0xE0}});
        MAGIC_BYTES.put("pptx", new byte[][]{{0x50, 0x4B, 0x03, 0x04}});

        // Archives
        MAGIC_BYTES.put("zip", new byte[][]{{0x50, 0x4B, 0x03, 0x04}});

        // Text files don't have magic bytes, validated by content-type only
    }

    // Map of extensions to allowed content types
    private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = new HashMap<>();

    static {
        ALLOWED_CONTENT_TYPES.put("jpg", Set.of("image/jpeg"));
        ALLOWED_CONTENT_TYPES.put("jpeg", Set.of("image/jpeg"));
        ALLOWED_CONTENT_TYPES.put("png", Set.of("image/png"));
        ALLOWED_CONTENT_TYPES.put("gif", Set.of("image/gif"));
        ALLOWED_CONTENT_TYPES.put("pdf", Set.of("application/pdf"));
        ALLOWED_CONTENT_TYPES.put("doc", Set.of("application/msword"));
        ALLOWED_CONTENT_TYPES.put("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        ALLOWED_CONTENT_TYPES.put("xls", Set.of("application/vnd.ms-excel"));
        ALLOWED_CONTENT_TYPES.put("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        ALLOWED_CONTENT_TYPES.put("ppt", Set.of("application/vnd.ms-powerpoint"));
        ALLOWED_CONTENT_TYPES.put("pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation"));
        ALLOWED_CONTENT_TYPES.put("txt", Set.of("text/plain"));
        ALLOWED_CONTENT_TYPES.put("zip", Set.of("application/zip", "application/x-zip-compressed"));
    }

    /**
     * Validate an uploaded file for security and compliance.
     *
     * @param file The uploaded file to validate
     * @return ValidationResult with success/failure and details
     */
    public ValidationResult validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ValidationResult.failure("File is empty or null");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return ValidationResult.failure("Invalid filename");
        }

        // Check for path traversal attacks
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            log.warn("Potential path traversal attack detected in filename: {}", originalFilename);
            return ValidationResult.failure("Invalid filename - contains illegal characters");
        }

        // Validate file size
        long maxBytes = (long) maxFileSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            return ValidationResult.failure("File size exceeds maximum allowed (" + maxFileSizeMb + "MB)");
        }

        // Validate extension
        String extension = getFileExtension(originalFilename).toLowerCase();
        Set<String> allowedExtSet = Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        if (!allowedExtSet.contains(extension)) {
            return ValidationResult.failure("File type not allowed: " + extension);
        }

        // Validate content type matches extension
        String contentType = file.getContentType();
        if (contentType != null && ALLOWED_CONTENT_TYPES.containsKey(extension)) {
            Set<String> allowedTypes = ALLOWED_CONTENT_TYPES.get(extension);
            if (!allowedTypes.contains(contentType.toLowerCase())) {
                log.warn("Content-type mismatch: expected {} for extension {}, got {}",
                        allowedTypes, extension, contentType);
                return ValidationResult.failure("Content type does not match file extension");
            }
        }

        // Validate magic bytes (file signature) for binary files
        if (MAGIC_BYTES.containsKey(extension)) {
            try {
                if (!validateMagicBytes(file, extension)) {
                    log.warn("Magic bytes validation failed for file: {} (claimed extension: {})",
                            originalFilename, extension);
                    return ValidationResult.failure("File content does not match declared type");
                }
            } catch (IOException e) {
                log.error("Error reading file for magic byte validation", e);
                return ValidationResult.failure("Could not validate file content");
            }
        }

        // Check for null bytes in filename (potential exploit)
        if (originalFilename.contains("\0")) {
            log.warn("Null byte detected in filename: {}", originalFilename);
            return ValidationResult.failure("Invalid filename - contains null bytes");
        }

        // Check for double extensions (e.g., malware.jpg.exe)
        if (hasDoubleExtension(originalFilename)) {
            log.warn("Double extension detected in filename: {}", originalFilename);
            return ValidationResult.failure("Files with double extensions are not allowed");
        }

        log.debug("File validation passed: {} ({} bytes, type: {})",
                originalFilename, file.getSize(), contentType);
        return ValidationResult.success(extension, contentType);
    }

    /**
     * Validate file magic bytes against expected signatures
     */
    private boolean validateMagicBytes(MultipartFile file, String extension) throws IOException {
        byte[][] expectedSignatures = MAGIC_BYTES.get(extension);
        if (expectedSignatures == null) {
            return true; // No signature to validate
        }

        try (InputStream is = file.getInputStream()) {
            // Read enough bytes for the longest signature
            int maxLength = 0;
            for (byte[] sig : expectedSignatures) {
                maxLength = Math.max(maxLength, sig.length);
            }

            byte[] fileHeader = new byte[maxLength];
            int bytesRead = is.read(fileHeader);

            if (bytesRead < expectedSignatures[0].length) {
                return false; // File too small to contain signature
            }

            // Check if any of the expected signatures match
            for (byte[] signature : expectedSignatures) {
                if (bytesRead >= signature.length) {
                    boolean matches = true;
                    for (int i = 0; i < signature.length; i++) {
                        if (fileHeader[i] != signature[i]) {
                            matches = false;
                            break;
                        }
                    }
                    if (matches) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if filename has a suspicious double extension
     */
    private boolean hasDoubleExtension(String filename) {
        String[] dangerousExtensions = {"exe", "bat", "cmd", "sh", "ps1", "vbs", "js", "jar", "msi"};
        String lowerFilename = filename.toLowerCase();

        for (String ext : dangerousExtensions) {
            if (lowerFilename.contains("." + ext + ".") || lowerFilename.endsWith("." + ext)) {
                // Check if this is a hidden dangerous extension
                int lastDot = lowerFilename.lastIndexOf('.');
                int secondLastDot = lowerFilename.lastIndexOf('.', lastDot - 1);
                if (secondLastDot > 0) {
                    String middleExt = lowerFilename.substring(secondLastDot + 1, lastDot);
                    for (String safe : new String[]{"jpg", "png", "gif", "pdf", "doc", "txt"}) {
                        if (middleExt.equals(safe)) {
                            return true; // Pattern like "file.jpg.exe"
                        }
                    }
                }
            }
        }
        return false;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /**
     * Result of file validation
     */
    @Getter
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final String extension;
        private final String contentType;

        private ValidationResult(boolean valid, String message, String extension, String contentType) {
            this.valid = valid;
            this.message = message;
            this.extension = extension;
            this.contentType = contentType;
        }

        public static ValidationResult success(String extension, String contentType) {
            return new ValidationResult(true, "Valid", extension, contentType);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message, null, null);
        }
    }
}
