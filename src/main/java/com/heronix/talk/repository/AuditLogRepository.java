package com.heronix.talk.repository;

import com.heronix.talk.model.domain.AuditLog;
import com.heronix.talk.model.enums.AuditAction;
import com.heronix.talk.model.enums.AuditCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for AuditLog entity operations.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByOrderByTimestampDesc(Pageable pageable);

    Page<AuditLog> findByCategoryOrderByTimestampDesc(AuditCategory category, Pageable pageable);

    Page<AuditLog> findByActionOrderByTimestampDesc(AuditAction action, Pageable pageable);

    Page<AuditLog> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.timestamp >= :since ORDER BY a.timestamp DESC")
    Page<AuditLog> findSince(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.category = :category AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findByCategorySince(@Param("category") AuditCategory category, @Param("since") LocalDateTime since);

    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT a FROM AuditLog a WHERE a.success = false AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findFailedSince(@Param("since") LocalDateTime since);

    @Query("SELECT a FROM AuditLog a WHERE a.action IN ('LOGIN_FAILED', 'ACCOUNT_LOCKED') AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findSecurityEventsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.action = 'LOGIN_FAILED' AND a.timestamp >= :since")
    long countFailedLoginsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.action = 'ACCOUNT_LOCKED' AND a.timestamp >= :since")
    long countLockedAccountsSince(@Param("since") LocalDateTime since);

    // Search functionality
    @Query("SELECT a FROM AuditLog a WHERE " +
            "(a.username LIKE %:term% OR a.description LIKE %:term% OR a.targetName LIKE %:term%) " +
            "ORDER BY a.timestamp DESC")
    Page<AuditLog> search(@Param("term") String term, Pageable pageable);

    // Cleanup old logs
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);

    // Statistics
    @Query("SELECT a.action, COUNT(a) FROM AuditLog a WHERE a.timestamp >= :since GROUP BY a.action")
    List<Object[]> countByActionSince(@Param("since") LocalDateTime since);

    @Query("SELECT a.category, COUNT(a) FROM AuditLog a WHERE a.timestamp >= :since GROUP BY a.category")
    List<Object[]> countByCategorySince(@Param("since") LocalDateTime since);

    // Recent activity
    @Query("SELECT a FROM AuditLog a ORDER BY a.timestamp DESC")
    List<AuditLog> findRecent(Pageable pageable);
}
