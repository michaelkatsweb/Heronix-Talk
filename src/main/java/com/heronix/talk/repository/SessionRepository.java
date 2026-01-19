package com.heronix.talk.repository;

import com.heronix.talk.model.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for UserSession entity operations.
 */
@Repository
public interface SessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findBySessionToken(String sessionToken);

    @Query("SELECT s FROM UserSession s WHERE s.sessionToken = :token AND s.active = true")
    Optional<UserSession> findValidSession(@Param("token") String token);

    List<UserSession> findByUserIdAndActiveTrue(Long userId);

    @Query("SELECT s FROM UserSession s WHERE s.user.id = :userId AND s.active = true")
    List<UserSession> findActiveSessionsByUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.active = true")
    long countActiveSessions();

    @Query("SELECT COUNT(DISTINCT s.user.id) FROM UserSession s WHERE s.active = true")
    long countActiveUsers();

    @Modifying
    @Query("UPDATE UserSession s SET s.lastActivityAt = :time WHERE s.sessionToken = :token")
    void updateLastActivity(@Param("token") String token, @Param("time") LocalDateTime time);

    @Modifying
    @Query("UPDATE UserSession s SET s.active = false WHERE s.sessionToken = :token")
    void invalidateSession(@Param("token") String token);

    @Modifying
    @Query("UPDATE UserSession s SET s.active = false WHERE s.user.id = :userId")
    void invalidateAllUserSessions(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :before")
    int deleteExpiredSessions(@Param("before") LocalDateTime before);

    @Modifying
    @Query("UPDATE UserSession s SET s.active = false WHERE s.expiresAt IS NOT NULL AND s.expiresAt < :now AND s.active = true")
    int expireOldSessions(@Param("now") LocalDateTime now);
}
