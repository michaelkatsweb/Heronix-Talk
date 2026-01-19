package com.heronix.talk.repository;

import com.heronix.talk.model.domain.SecurityPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for SecurityPolicy entity operations.
 */
@Repository
public interface SecurityPolicyRepository extends JpaRepository<SecurityPolicy, Long> {

    Optional<SecurityPolicy> findByName(String name);

    Optional<SecurityPolicy> findByDefaultPolicyTrue();

    List<SecurityPolicy> findByActiveTrue();

    @Modifying
    @Query("UPDATE SecurityPolicy p SET p.defaultPolicy = false WHERE p.id != :policyId")
    void clearDefaultExcept(@Param("policyId") Long policyId);

    @Modifying
    @Query("UPDATE SecurityPolicy p SET p.active = :active WHERE p.id = :policyId")
    void updateActiveStatus(@Param("policyId") Long policyId, @Param("active") boolean active);

    boolean existsByName(String name);
}
