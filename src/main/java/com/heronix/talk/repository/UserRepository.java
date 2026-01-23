package com.heronix.talk.repository;

import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.enums.SyncStatus;
import com.heronix.talk.model.enums.UserRole;
import com.heronix.talk.model.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmployeeId(String employeeId);

    Optional<User> findByEmail(String email);

    List<User> findByActiveTrue();

    List<User> findByActiveTrueOrderByLastNameAsc();

    List<User> findByRole(UserRole role);

    List<User> findByRoleAndActiveTrue(UserRole role);

    List<User> findByDepartment(String department);

    List<User> findByDepartmentAndActiveTrue(String department);

    List<User> findByStatus(UserStatus status);

    List<User> findByStatusNot(UserStatus status);

    @Query("SELECT u FROM User u WHERE u.status != 'OFFLINE' AND u.active = true")
    List<User> findOnlineUsers();

    @Query("SELECT u FROM User u WHERE u.syncStatus = :status")
    List<User> findBySyncStatus(@Param("status") SyncStatus status);

    @Query("SELECT u FROM User u WHERE u.syncStatus IN ('PENDING', 'LOCAL_ONLY')")
    List<User> findNeedingSync();

    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :term, '%')) " +
            "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :term, '%')) " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<User> searchByTerm(@Param("term") String term);

    @Query("SELECT u FROM User u WHERE u.active = true AND " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :term, '%')) " +
            "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :term, '%')))")
    List<User> searchActiveByName(@Param("term") String term);

    @Modifying
    @Query("UPDATE User u SET u.status = :status, u.lastSeen = :lastSeen WHERE u.id = :userId")
    void updateStatus(@Param("userId") Long userId,
                      @Param("status") UserStatus status,
                      @Param("lastSeen") LocalDateTime lastSeen);

    @Modifying
    @Query("UPDATE User u SET u.lastActivity = :lastActivity WHERE u.id = :userId")
    void updateLastActivity(@Param("userId") Long userId,
                            @Param("lastActivity") LocalDateTime lastActivity);

    @Modifying
    @Query("UPDATE User u SET u.syncStatus = :status, u.lastSyncTime = :syncTime WHERE u.id = :userId")
    void updateSyncStatus(@Param("userId") Long userId,
                          @Param("status") SyncStatus status,
                          @Param("syncTime") LocalDateTime syncTime);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmployeeId(String employeeId);

    long countByActiveTrue();

    long countByStatus(UserStatus status);

    @Query("SELECT COUNT(u) FROM User u WHERE u.status != 'OFFLINE' AND u.active = true")
    long countOnlineUsers();

    long countByLockedTrue();

    /**
     * Find all users synced from a specific source (e.g., "SIS", "SIS-AUTO").
     */
    @Query("SELECT u FROM User u WHERE u.syncSource LIKE CONCAT('%', :source, '%')")
    List<User> findBySyncSourceContaining(@Param("source") String source);

    /**
     * Find all active users synced from SIS.
     */
    @Query("SELECT u FROM User u WHERE u.syncSource LIKE '%SIS%' AND u.active = true")
    List<User> findActiveSisUsers();
}
