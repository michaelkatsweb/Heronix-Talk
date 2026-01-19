package com.heronix.talk.repository;

import com.heronix.talk.model.domain.NewsItem;
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

/**
 * Repository for NewsItem entity operations.
 */
@Repository
public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

    List<NewsItem> findByActiveTrue();

    @Query("SELECT n FROM NewsItem n WHERE n.active = true AND (n.expiresAt IS NULL OR n.expiresAt > :now) ORDER BY n.priority DESC, n.publishedAt DESC")
    List<NewsItem> findVisibleNews(@Param("now") LocalDateTime now);

    @Query("SELECT n FROM NewsItem n WHERE n.active = true AND (n.expiresAt IS NULL OR n.expiresAt > :now) ORDER BY n.priority DESC, n.publishedAt DESC")
    Page<NewsItem> findVisibleNewsPaged(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT n FROM NewsItem n WHERE n.pinned = true AND n.active = true AND (n.expiresAt IS NULL OR n.expiresAt > :now) ORDER BY n.publishedAt DESC")
    List<NewsItem> findPinnedNews(@Param("now") LocalDateTime now);

    @Query("SELECT n FROM NewsItem n WHERE n.urgent = true AND n.active = true AND (n.expiresAt IS NULL OR n.expiresAt > :now) ORDER BY n.publishedAt DESC")
    List<NewsItem> findUrgentNews(@Param("now") LocalDateTime now);

    List<NewsItem> findByCategory(String category);

    @Query("SELECT n FROM NewsItem n WHERE n.category = :category AND n.active = true AND (n.expiresAt IS NULL OR n.expiresAt > :now)")
    List<NewsItem> findVisibleByCategory(@Param("category") String category, @Param("now") LocalDateTime now);

    List<NewsItem> findByAuthorId(Long authorId);

    @Query("SELECT n FROM NewsItem n WHERE n.syncStatus = :status")
    List<NewsItem> findBySyncStatus(@Param("status") SyncStatus status);

    @Query("SELECT DISTINCT n.category FROM NewsItem n WHERE n.category IS NOT NULL ORDER BY n.category")
    List<String> findAllCategories();

    @Modifying
    @Query("UPDATE NewsItem n SET n.viewCount = n.viewCount + 1 WHERE n.id = :newsId")
    void incrementViewCount(@Param("newsId") Long newsId);

    @Modifying
    @Query("UPDATE NewsItem n SET n.active = false WHERE n.expiresAt < :now AND n.active = true")
    int deactivateExpired(@Param("now") LocalDateTime now);

    long countByActiveTrue();

    @Query("SELECT COUNT(n) FROM NewsItem n WHERE n.urgent = true AND n.active = true AND (n.expiresAt IS NULL OR n.expiresAt > :now)")
    long countUrgent(@Param("now") LocalDateTime now);
}
