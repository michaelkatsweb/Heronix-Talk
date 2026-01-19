package com.heronix.talk.repository;

import com.heronix.talk.model.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for UserRole entity operations.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    Optional<UserRole> findByName(String name);

    List<UserRole> findByActiveTrue();

    List<UserRole> findByActiveTrueOrderByPriorityDesc();

    List<UserRole> findBySystemRoleTrue();

    @Query("SELECT r FROM UserRole r WHERE r.active = true AND r.systemRole = false ORDER BY r.priority DESC")
    List<UserRole> findEditableRoles();

    boolean existsByName(String name);

    @Query("SELECT r FROM UserRole r WHERE r.canManageUsers = true AND r.active = true")
    List<UserRole> findRolesWithUserManagement();

    @Query("SELECT r FROM UserRole r WHERE r.canSendEmergencyAlerts = true AND r.active = true")
    List<UserRole> findRolesWithEmergencyAccess();
}
