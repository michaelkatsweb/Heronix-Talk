package com.heronix.talk.controller;

import com.heronix.talk.model.dto.NewsItemDTO;
import com.heronix.talk.service.AuthenticationService;
import com.heronix.talk.service.NewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for news ticker operations.
 */
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
@Slf4j
public class NewsController {

    private final NewsService newsService;
    private final AuthenticationService authenticationService;

    @GetMapping
    public ResponseEntity<List<NewsItemDTO>> getVisibleNews(
            @RequestParam(required = false) Integer limit) {
        if (limit != null && limit > 0) {
            return ResponseEntity.ok(newsService.getVisibleNews(limit));
        }
        return ResponseEntity.ok(newsService.getVisibleNews());
    }

    @GetMapping("/pinned")
    public ResponseEntity<List<NewsItemDTO>> getPinnedNews() {
        return ResponseEntity.ok(newsService.getPinnedNews());
    }

    @GetMapping("/urgent")
    public ResponseEntity<List<NewsItemDTO>> getUrgentNews() {
        return ResponseEntity.ok(newsService.getUrgentNews());
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<NewsItemDTO>> getNewsByCategory(@PathVariable String category) {
        return ResponseEntity.ok(newsService.getNewsByCategory(category));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getAllCategories() {
        return ResponseEntity.ok(newsService.getAllCategories());
    }

    @PostMapping
    public ResponseEntity<NewsItemDTO> createNewsItem(
            @RequestBody Map<String, String> request,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    var newsItem = newsService.createNewsItem(
                            request.get("headline"),
                            request.get("content"),
                            request.get("category"),
                            user
                    );
                    return ResponseEntity.ok(NewsItemDTO.fromEntity(newsItem));
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/urgent")
    public ResponseEntity<NewsItemDTO> createUrgentNews(
            @RequestBody Map<String, String> request,
            @RequestHeader("X-Session-Token") String sessionToken) {
        return authenticationService.getUserFromSession(sessionToken)
                .map(user -> {
                    var newsItem = newsService.createUrgentNews(
                            request.get("headline"),
                            request.get("content"),
                            user
                    );
                    return ResponseEntity.ok(NewsItemDTO.fromEntity(newsItem));
                })
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<Void> recordView(@PathVariable Long id) {
        newsService.incrementViewCount(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/pin")
    public ResponseEntity<Void> pinNews(
            @PathVariable Long id,
            @RequestParam boolean pinned) {
        newsService.pinNewsItem(id, pinned);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateNews(@PathVariable Long id) {
        newsService.deactivateNewsItem(id);
        return ResponseEntity.ok().build();
    }
}
