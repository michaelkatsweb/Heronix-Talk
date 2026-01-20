package com.heronix.talk.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing the result of a user sync or import operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultDTO {

    private boolean success;
    private String source;
    private int totalProcessed;
    private int created;
    private int updated;
    private int skipped;
    private int errors;

    @Builder.Default
    private List<String> errorMessages = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    private long durationMs;

    public void addError(String message) {
        if (errorMessages == null) {
            errorMessages = new ArrayList<>();
        }
        errorMessages.add(message);
        errors++;
    }

    public void addWarning(String message) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(message);
    }

    public void incrementCreated() {
        created++;
        totalProcessed++;
    }

    public void incrementUpdated() {
        updated++;
        totalProcessed++;
    }

    public void incrementSkipped() {
        skipped++;
        totalProcessed++;
    }

    public static ImportResultDTO createError(String source, String errorMessage) {
        ImportResultDTO result = ImportResultDTO.builder()
                .success(false)
                .source(source)
                .timestamp(LocalDateTime.now())
                .errorMessages(new ArrayList<>())
                .warnings(new ArrayList<>())
                .build();
        result.addError(errorMessage);
        return result;
    }
}
