package com.heronix.talk.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.heronix.talk.model.domain.Channel;
import com.heronix.talk.model.enums.ChannelType;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for Channel entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelDTO {

    private Long id;
    private String name;
    private String description;
    private ChannelType channelType;
    private String icon;
    private Long creatorId;
    private String creatorName;
    private int memberCount;
    private int messageCount;
    private boolean active;
    private boolean archived;
    private boolean pinned;
    private String directMessageKey;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastMessageTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdDate;

    // User-specific fields (populated based on membership)
    private boolean muted;
    private boolean favorite;
    private int unreadCount;
    private Long lastReadMessageId;

    public static ChannelDTO fromEntity(Channel channel) {
        if (channel == null) return null;
        return ChannelDTO.builder()
                .id(channel.getId())
                .name(channel.getName())
                .description(channel.getDescription())
                .channelType(channel.getChannelType())
                .icon(channel.getIcon())
                .creatorId(channel.getCreator() != null ? channel.getCreator().getId() : null)
                .creatorName(channel.getCreator() != null ? channel.getCreator().getFullName() : null)
                .memberCount(channel.getMemberCount())
                .messageCount(channel.getMessageCount())
                .active(channel.isActive())
                .archived(channel.isArchived())
                .pinned(channel.isPinned())
                .directMessageKey(channel.getDirectMessageKey())
                .lastMessageTime(channel.getLastMessageTime())
                .createdDate(channel.getCreatedDate())
                .build();
    }

    public boolean isDirectMessage() {
        return channelType == ChannelType.DIRECT_MESSAGE;
    }

    public boolean isPublic() {
        return channelType == ChannelType.PUBLIC;
    }
}
