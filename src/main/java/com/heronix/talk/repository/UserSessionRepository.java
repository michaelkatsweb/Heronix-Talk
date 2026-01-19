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
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findBySessionToken(String sessionToken);

    @Query("SELECT us FROM UserSession us JOIN FETCH us.user WHERE us.sessionToken = :token")
    Optional<UserSession> findBySessionTokenWithUser(@Param("token") String token);

    Optional<UserSession> findByWebsocketSessionId(String websocketSessionId);

    List<UserSession> findByUserId(Long userId);

    List<UserSession> findByUserIdAndActiveTrue(Long userId);

    @Query("SELECT us FROM UserSession us WHERE us.active = true")
    List<UserSession> findActiveSessions();

    @Query("SELECT us FROM UserSession us WHERE us.active = true AND us.lastActivityAt < :threshold")
    List<UserSession> findStaleActiveSessions(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT us FROM UserSession us WHERE us.user.id = :userId AND us.active = true AND us.websocketSessionId IS NOT NULL")
    List<UserSession> findActiveWebsocketSessionsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE UserSession us SET us.lastActivityAt = :lastActivity WHERE us.sessionToken = :token")
    void updateLastActivity(@Param("token") String token, @Param("lastActivity") LocalDateTime lastActivity);

    @Modifying
    @Query("UPDATE UserSession us SET us.websocketSessionId = :wsSessionId WHERE us.sessionToken = :token")
    void updateWebsocketSessionId(@Param("token") String token, @Param("wsSessionId") String wsSessionId);

    @Modifying
    @Query("UPDATE UserSession us SET us.active = false, us.disconnectedAt = :disconnectedAt WHERE us.sessionToken = :token")
    void deactivateSession(@Param("token") String token, @Param("disconnectedAt") LocalDateTime disconnectedAt);

    @Modifying
    @Query("UPDATE UserSession us SET us.active = false, us.disconnectedAt = :disconnectedAt WHERE us.websocketSessionId = :wsSessionId")
    void deactivateByWebsocketSessionId(@Param("wsSessionId") String wsSessionId, @Param("disconnectedAt") LocalDateTime disconnectedAt);

    @Modifying
    @Query("UPDATE UserSession us SET us.active = false, us.disconnectedAt = :now WHERE us.user.id = :userId AND us.active = true")
    void deactivateAllUserSessions(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UserSession us SET us.active = false, us.disconnectedAt = :now WHERE us.active = true AND us.lastActivityAt < :threshold")
    int deactivateStaleSessions(@Param("threshold") LocalDateTime threshold, @Param("now") LocalDateTime now);

    boolean existsBySessionTokenAndActiveTrue(String sessionToken);

    long countByActiveTrue();

    long countByUserIdAndActiveTrue(Long userId);

    @Query("SELECT DISTINCT us.user.id FROM UserSession us WHERE us.active = true")
    List<Long> findDistinctActiveUserIds();
}
