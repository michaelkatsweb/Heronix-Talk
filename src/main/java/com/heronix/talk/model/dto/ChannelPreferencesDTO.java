package com.heronix.talk.model.dto;

import lombok.*;

/**
 * DTO for updating channel-specific preferences per user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelPreferencesDTO {

    private Long channelId;
    private Boolean muted;
    private Boolean pinned;
    private Boolean favorite;
    private Boolean notifyOnMessage;
    private Boolean notifyOnMention;
}
