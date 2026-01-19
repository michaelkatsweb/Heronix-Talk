package com.heronix.talk.service;

import com.heronix.talk.model.domain.NewsItem;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.NewsItemDTO;
import com.heronix.talk.model.enums.SyncStatus;
import com.heronix.talk.repository.NewsItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for news ticker operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {

    private final NewsItemRepository newsItemRepository;

    public Optional<NewsItem> findById(Long id) {
        return newsItemRepository.findById(id);
    }

    public List<NewsItemDTO> getVisibleNews() {
        return newsItemRepository.findVisibleNews(LocalDateTime.now()).stream()
                .map(NewsItemDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<NewsItemDTO> getVisibleNews(int limit) {
        return newsItemRepository.findVisibleNewsPaged(LocalDateTime.now(), PageRequest.of(0, limit))
                .getContent().stream()
                .map(NewsItemDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<NewsItemDTO> getPinnedNews() {
        return newsItemRepository.findPinnedNews(LocalDateTime.now()).stream()
                .map(NewsItemDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<NewsItemDTO> getUrgentNews() {
        return newsItemRepository.findUrgentNews(LocalDateTime.now()).stream()
                .map(NewsItemDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<NewsItemDTO> getNewsByCategory(String category) {
        return newsItemRepository.findVisibleByCategory(category, LocalDateTime.now()).stream()
                .map(NewsItemDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<String> getAllCategories() {
        return newsItemRepository.findAllCategories();
    }

    @Transactional
    public NewsItem createNewsItem(String headline, String content, String category, User author) {
        log.info("Creating news item: {} by {}", headline, author.getUsername());

        NewsItem newsItem = NewsItem.builder()
                .headline(headline)
                .content(content)
                .category(category)
                .author(author)
                .publishedAt(LocalDateTime.now())
                .syncStatus(SyncStatus.LOCAL_ONLY)
                .build();

        return newsItemRepository.save(newsItem);
    }

    @Transactional
    public NewsItem createUrgentNews(String headline, String content, User author) {
        NewsItem newsItem = NewsItem.builder()
                .headline(headline)
                .content(content)
                .author(author)
                .urgent(true)
                .priority(100)
                .publishedAt(LocalDateTime.now())
                .syncStatus(SyncStatus.LOCAL_ONLY)
                .build();

        log.info("Created urgent news: {}", headline);
        return newsItemRepository.save(newsItem);
    }

    @Transactional
    public NewsItem updateNewsItem(NewsItem newsItem) {
        return newsItemRepository.save(newsItem);
    }

    @Transactional
    public void pinNewsItem(Long newsId, boolean pinned) {
        newsItemRepository.findById(newsId).ifPresent(news -> {
            news.setPinned(pinned);
            newsItemRepository.save(news);
            log.info("News item {} {}", newsId, pinned ? "pinned" : "unpinned");
        });
    }

    @Transactional
    public void deactivateNewsItem(Long newsId) {
        newsItemRepository.findById(newsId).ifPresent(news -> {
            news.setActive(false);
            newsItemRepository.save(news);
            log.info("News item {} deactivated", newsId);
        });
    }

    @Transactional
    public void incrementViewCount(Long newsId) {
        newsItemRepository.incrementViewCount(newsId);
    }

    @Transactional
    public int cleanupExpiredNews() {
        int deactivated = newsItemRepository.deactivateExpired(LocalDateTime.now());
        if (deactivated > 0) {
            log.info("Deactivated {} expired news items", deactivated);
        }
        return deactivated;
    }

    public long getActiveNewsCount() {
        return newsItemRepository.countByActiveTrue();
    }

    public long getUrgentNewsCount() {
        return newsItemRepository.countUrgent(LocalDateTime.now());
    }
}
