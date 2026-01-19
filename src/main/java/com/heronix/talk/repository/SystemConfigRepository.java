package com.heronix.talk.repository;

import com.heronix.talk.model.domain.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for SystemConfig entity operations.
 */
@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    Optional<SystemConfig> findByConfigKey(String configKey);

    List<SystemConfig> findByCategory(String category);

    List<SystemConfig> findByCategoryOrderByConfigKeyAsc(String category);

    @Query("SELECT c FROM SystemConfig c WHERE c.sensitive = false ORDER BY c.category, c.configKey")
    List<SystemConfig> findAllNonSensitive();

    @Query("SELECT c FROM SystemConfig c ORDER BY c.category, c.configKey")
    List<SystemConfig> findAllOrderedByCategoryAndKey();

    boolean existsByConfigKey(String configKey);

    @Query("SELECT c.configValue FROM SystemConfig c WHERE c.configKey = :key")
    Optional<String> findValueByKey(@Param("key") String key);

    @Query("SELECT DISTINCT c.category FROM SystemConfig c ORDER BY c.category")
    List<String> findAllCategories();

    List<SystemConfig> findByReadonlyFalse();
}
