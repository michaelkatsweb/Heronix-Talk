package com.heronix.talk.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heronix.talk.model.domain.NewsItem;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for NewsItem entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsItemDTO {

    private Long id;
    private String headline;
    private String content;
    private String category;
    private Long authorId;
    private String authorName;
    private int priority;
    private boolean active;
    private boolean pinned;
    private boolean urgent;
    private String linkUrl;
    private String imagePath;
    private int viewCount;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime publishedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    public static NewsItemDTO fromEntity(NewsItem newsItem) {
        if (newsItem == null) return null;
        return NewsItemDTO.builder()
                .id(newsItem.getId())
                .headline(newsItem.getHeadline())
                .content(newsItem.getContent())
                .category(newsItem.getCategory())
                .authorId(newsItem.getAuthor() != null ? newsItem.getAuthor().getId() : null)
                .authorName(newsItem.getAuthor() != null ? newsItem.getAuthor().getFullName() : null)
                .priority(newsItem.getPriority())
                .active(newsItem.isActive())
                .pinned(newsItem.isPinned())
                .urgent(newsItem.isUrgent())
                .linkUrl(newsItem.getLinkUrl())
                .imagePath(newsItem.getImagePath())
                .viewCount(newsItem.getViewCount())
                .publishedAt(newsItem.getPublishedAt())
                .expiresAt(newsItem.getExpiresAt())
                .build();
    }
}
