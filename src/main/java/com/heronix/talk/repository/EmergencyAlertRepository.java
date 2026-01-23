package com.heronix.talk.repository;

import com.heronix.talk.model.domain.EmergencyAlert;
import com.heronix.talk.model.enums.AlertLevel;
import com.heronix.talk.model.enums.AlertType;
import com.heronix.talk.model.enums.SyncStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for EmergencyAlert entity operations.
 */
@Repository
public interface EmergencyAlertRepository extends JpaRepository<EmergencyAlert, Long> {

    Optional<EmergencyAlert> findByAlertUuid(String alertUuid);

    List<EmergencyAlert> findByActiveTrue();

    @Query("SELECT a FROM EmergencyAlert a LEFT JOIN FETCH a.issuedBy WHERE a.active = true AND (a.expiresAt IS NULL OR a.expiresAt > :now) ORDER BY a.alertLevel ASC, a.issuedAt DESC")
    List<EmergencyAlert> findActiveAlerts(@Param("now") LocalDateTime now);

    @Query("SELECT a FROM EmergencyAlert a LEFT JOIN FETCH a.issuedBy WHERE a.active = true AND (a.expiresAt IS NULL OR a.expiresAt > :now) ORDER BY a.alertLevel ASC, a.issuedAt DESC")
    Page<EmergencyAlert> findActiveAlertsPaged(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT a FROM EmergencyAlert a LEFT JOIN FETCH a.issuedBy WHERE a.alertLevel = :level AND a.active = true AND (a.expiresAt IS NULL OR a.expiresAt > :now) ORDER BY a.issuedAt DESC")
    List<EmergencyAlert> findActiveByLevel(@Param("level") AlertLevel level, @Param("now") LocalDateTime now);

    @Query("SELECT a FROM EmergencyAlert a LEFT JOIN FETCH a.issuedBy WHERE a.alertType = :type AND a.active = true AND (a.expiresAt IS NULL OR a.expiresAt > :now) ORDER BY a.issuedAt DESC")
    List<EmergencyAlert> findActiveByType(@Param("type") AlertType type, @Param("now") LocalDateTime now);

    @Query("SELECT a FROM EmergencyAlert a LEFT JOIN FETCH a.issuedBy WHERE a.alertLevel IN ('EMERGENCY', 'URGENT') AND a.active = true AND (a.expiresAt IS NULL OR a.expiresAt > :now) ORDER BY a.alertLevel ASC, a.issuedAt DESC")
    List<EmergencyAlert> findCriticalAlerts(@Param("now") LocalDateTime now);

    @Query("SELECT a FROM EmergencyAlert a WHERE a.issuedBy.id = :userId ORDER BY a.issuedAt DESC")
    List<EmergencyAlert> findByIssuerId(@Param("userId") Long userId);

    @Query("SELECT a FROM EmergencyAlert a WHERE a.syncStatus = :status")
    List<EmergencyAlert> findBySyncStatus(@Param("status") SyncStatus status);

    @Modifying
    @Query("UPDATE EmergencyAlert a SET a.acknowledgmentCount = a.acknowledgmentCount + 1 WHERE a.id = :alertId")
    void incrementAcknowledgment(@Param("alertId") Long alertId);

    @Modifying
    @Query("UPDATE EmergencyAlert a SET a.active = false, a.cancelledAt = :now WHERE a.expiresAt < :now AND a.active = true")
    int deactivateExpired(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE EmergencyAlert a SET a.active = false, a.cancelledAt = :now WHERE a.active = true")
    int cancelAllActive(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE EmergencyAlert a SET a.active = false, a.cancelledAt = :now WHERE a.alertLevel IN ('EMERGENCY', 'URGENT') AND a.active = true")
    int cancelAllEmergencies(@Param("now") LocalDateTime now);

    long countByActiveTrue();

    @Query("SELECT COUNT(a) FROM EmergencyAlert a WHERE a.alertLevel IN ('EMERGENCY', 'URGENT') AND a.active = true AND (a.expiresAt IS NULL OR a.expiresAt > :now)")
    long countCriticalActive(@Param("now") LocalDateTime now);
}
