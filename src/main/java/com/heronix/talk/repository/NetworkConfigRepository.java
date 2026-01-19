package com.heronix.talk.repository;

import com.heronix.talk.model.domain.NetworkConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for NetworkConfig entity operations.
 */
@Repository
public interface NetworkConfigRepository extends JpaRepository<NetworkConfig, Long> {

    Optional<NetworkConfig> findByName(String name);

    List<NetworkConfig> findByActiveTrue();

    Optional<NetworkConfig> findFirstByActiveTrue();

    @Modifying
    @Query("UPDATE NetworkConfig c SET c.active = :active WHERE c.id = :configId")
    void updateActiveStatus(@Param("configId") Long configId, @Param("active") boolean active);

    boolean existsByName(String name);
}
