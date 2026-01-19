package com.heronix.talk.model.dto;

import com.heronix.talk.model.enums.ChannelType;
import lombok.*;

import java.util.List;

/**
 * Request DTO for creating a new channel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateChannelRequest {
    private String name;
    private String description;
    private ChannelType channelType;
    private String icon;
    private List<Long> memberIds;
    private boolean notifyMembers;
}
