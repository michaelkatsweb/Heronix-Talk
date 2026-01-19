package com.heronix.talk.repository;

import com.heronix.talk.model.domain.PasswordHistory;
import com.heronix.talk.model.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for PasswordHistory entity operations.
 */
@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    @Query("SELECT ph FROM PasswordHistory ph WHERE ph.user = :user ORDER BY ph.createdAt DESC")
    List<PasswordHistory> findByUserOrderByCreatedAtDesc(@Param("user") User user);

    @Query("SELECT ph FROM PasswordHistory ph WHERE ph.user = :user ORDER BY ph.createdAt DESC")
    List<PasswordHistory> findRecentByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT ph.passwordHash FROM PasswordHistory ph WHERE ph.user = :user ORDER BY ph.createdAt DESC")
    List<String> findRecentHashesByUser(@Param("user") User user, Pageable pageable);

    @Modifying
    @Query("DELETE FROM PasswordHistory ph WHERE ph.user = :user")
    void deleteByUser(@Param("user") User user);

    long countByUser(User user);
}
