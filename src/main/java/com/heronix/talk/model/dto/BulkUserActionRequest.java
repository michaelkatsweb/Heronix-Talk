package com.heronix.talk.model.dto;

import lombok.*;
import java.util.List;

/**
 * Request DTO for bulk user actions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUserActionRequest {

    private List<Long> userIds;
    private String action; // LOCK, UNLOCK, DELETE, ACTIVATE, DEACTIVATE, ASSIGN_ROLE
    private Long roleId; // For ASSIGN_ROLE action
    private String reason;
}
