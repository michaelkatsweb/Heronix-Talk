package com.heronix.talk.service;

import com.heronix.talk.model.domain.Channel;
import com.heronix.talk.model.domain.ChannelMembership;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.ChannelDTO;
import com.heronix.talk.model.dto.CreateChannelRequest;
import com.heronix.talk.model.enums.ChannelType;
import com.heronix.talk.model.enums.SyncStatus;
import com.heronix.talk.repository.ChannelMembershipRepository;
import com.heronix.talk.repository.ChannelRepository;
import com.heronix.talk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for channel management operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<Channel> findById(Long id) {
        return channelRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ChannelDTO> getAllActiveChannels() {
        return channelRepository.findByActiveTrueOrderByLastMessageTimeDesc().stream()
                .map(ChannelDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChannelDTO> getPublicChannels() {
        return channelRepository.findPublicChannels().stream()
                .map(ChannelDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChannelDTO> getAnnouncementChannels() {
        return channelRepository.findAnnouncementChannels().stream()
                .map(ChannelDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChannelDTO> getUserChannels(Long userId) {
        List<Channel> channels = channelRepository.findChannelsByMemberId(userId);
        return channels.stream()
                .map(channel -> {
                    ChannelDTO dto = ChannelDTO.fromEntity(channel);
                    membershipRepository.findByUserIdAndChannelId(userId, channel.getId())
                            .ifPresent(membership -> {
                                dto.setMuted(membership.isMuted());
                                dto.setFavorite(membership.isFavorite());
                                dto.setUnreadCount(membership.getUnreadCount());
                                dto.setLastReadMessageId(membership.getLastReadMessageId());
                            });
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChannelDTO> getUserDirectMessages(Long userId) {
        return channelRepository.findChannelsByMemberIdAndType(userId, ChannelType.DIRECT_MESSAGE).stream()
                .map(ChannelDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChannelDTO> searchChannels(String term) {
        return channelRepository.searchByName(term).stream()
                .map(ChannelDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public Channel createChannel(CreateChannelRequest request, User creator) {
        log.info("Creating channel: {} by user {}", request.getName(), creator.getUsername());

        Channel channel = Channel.builder()
                .name(request.getName())
                .description(request.getDescription())
                .channelType(request.getChannelType())
                .icon(request.getIcon())
                .creator(creator)
                .syncStatus(SyncStatus.LOCAL_ONLY)
                .build();

        final Channel savedChannel = channelRepository.save(channel);

        // Add creator as admin member
        addMember(savedChannel, creator, true, false);

        // Add other members if specified
        if (request.getMemberIds() != null) {
            for (Long memberId : request.getMemberIds()) {
                if (!memberId.equals(creator.getId())) {
                    userRepository.findById(memberId).ifPresent(user ->
                            addMember(savedChannel, user, false, false));
                }
            }
        }

        savedChannel.setMemberCount((int) membershipRepository.countByChannelIdAndActiveTrue(savedChannel.getId()));
        return channelRepository.save(savedChannel);
    }

    @Transactional
    public Channel getOrCreateDirectMessage(User user1, User user2) {
        String dmKey = Channel.generateDirectMessageKey(user1.getId(), user2.getId());

        return channelRepository.findByDirectMessageKey(dmKey)
                .orElseGet(() -> {
                    log.info("Creating DM channel between {} and {}", user1.getUsername(), user2.getUsername());

                    Channel dm = Channel.builder()
                            .name(user1.getFullName() + " & " + user2.getFullName())
                            .channelType(ChannelType.DIRECT_MESSAGE)
                            .directMessageKey(dmKey)
                            .syncStatus(SyncStatus.LOCAL_ONLY)
                            .build();

                    dm = channelRepository.save(dm);

                    addMember(dm, user1, false, false);
                    addMember(dm, user2, false, false);

                    dm.setMemberCount(2);
                    return channelRepository.save(dm);
                });
    }

    @Transactional
    public void addMember(Channel channel, User user, boolean isAdmin, boolean isModerator) {
        if (membershipRepository.existsByUserIdAndChannelIdAndActiveTrue(user.getId(), channel.getId())) {
            log.debug("User {} is already a member of channel {}", user.getUsername(), channel.getName());
            return;
        }

        ChannelMembership membership = ChannelMembership.builder()
                .channel(channel)
                .user(user)
                .isAdmin(isAdmin)
                .isModerator(isModerator)
                .build();

        membershipRepository.save(membership);

        // Update member count on the channel
        long memberCount = membershipRepository.countByChannelIdAndActiveTrue(channel.getId());
        channel.setMemberCount((int) memberCount);
        channelRepository.save(channel);

        log.info("User {} added to channel {}", user.getUsername(), channel.getName());
    }

    @Transactional
    public void removeMember(Long channelId, Long userId) {
        membershipRepository.findByUserIdAndChannelId(userId, channelId)
                .ifPresent(membership -> {
                    membership.leave();
                    membershipRepository.save(membership);
                    log.info("User {} removed from channel {}", userId, channelId);
                });

        channelRepository.findById(channelId).ifPresent(channel -> {
            channel.updateMemberCount();
            channelRepository.save(channel);
        });
    }

    @Transactional
    public void updateChannel(Channel channel) {
        channelRepository.save(channel);
    }

    @Transactional
    public void archiveChannel(Long channelId) {
        channelRepository.findById(channelId).ifPresent(channel -> {
            channel.setArchived(true);
            channel.setActive(false);
            channelRepository.save(channel);
            log.info("Channel {} archived", channel.getName());
        });
    }

    @Transactional
    public void deleteChannel(Long channelId) {
        channelRepository.findById(channelId).ifPresent(channel -> {
            channel.setActive(false);
            channelRepository.save(channel);
            log.info("Channel {} deleted (soft)", channel.getName());
        });
    }

    @Transactional
    public void updateLastMessageTime(Long channelId) {
        channelRepository.findById(channelId).ifPresent(channel -> {
            channel.setLastMessageTime(LocalDateTime.now());
            channel.incrementMessageCount();
            channelRepository.save(channel);
        });
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long channelId, Long userId) {
        return membershipRepository.existsByUserIdAndChannelIdAndActiveTrue(userId, channelId);
    }

    @Transactional(readOnly = true)
    public List<Long> getChannelMemberIds(Long channelId) {
        return membershipRepository.findByChannelIdAndActiveTrue(channelId).stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getMemberCount(Long channelId) {
        return membershipRepository.countByChannelIdAndActiveTrue(channelId);
    }

    /**
     * Update channel-specific preferences for a user.
     */
    @Transactional
    public void updateChannelPreferences(Long channelId, Long userId, Boolean muted, Boolean pinned,
                                         Boolean favorite, Boolean notifyOnMessage, Boolean notifyOnMention) {
        membershipRepository.findByUserIdAndChannelId(userId, channelId).ifPresent(membership -> {
            if (muted != null) {
                membership.setMuted(muted);
            }
            if (pinned != null) {
                membership.setPinned(pinned);
            }
            if (favorite != null) {
                membership.setFavorite(favorite);
            }
            if (notifyOnMessage != null) {
                membership.setNotifyOnMessage(notifyOnMessage);
            }
            if (notifyOnMention != null) {
                membership.setNotifyOnMention(notifyOnMention);
            }
            membershipRepository.save(membership);
            log.info("Channel {} preferences updated for user {}", channelId, userId);
        });
    }

    /**
     * Toggle mute status for a channel.
     */
    @Transactional
    public void toggleMute(Long channelId, Long userId) {
        membershipRepository.findByUserIdAndChannelId(userId, channelId).ifPresent(membership -> {
            membership.setMuted(!membership.isMuted());
            membershipRepository.save(membership);
            log.info("Channel {} mute toggled to {} for user {}", channelId, membership.isMuted(), userId);
        });
    }

    /**
     * Toggle favorite status for a channel.
     */
    @Transactional
    public void toggleFavorite(Long channelId, Long userId) {
        membershipRepository.findByUserIdAndChannelId(userId, channelId).ifPresent(membership -> {
            membership.setFavorite(!membership.isFavorite());
            membershipRepository.save(membership);
            log.info("Channel {} favorite toggled to {} for user {}", channelId, membership.isFavorite(), userId);
        });
    }

    /**
     * Toggle pin status for a channel.
     */
    @Transactional
    public void togglePin(Long channelId, Long userId) {
        membershipRepository.findByUserIdAndChannelId(userId, channelId).ifPresent(membership -> {
            membership.setPinned(!membership.isPinned());
            membershipRepository.save(membership);
            log.info("Channel {} pin toggled to {} for user {}", channelId, membership.isPinned(), userId);
        });
    }

    /**
     * Auto-join user to all public and announcement channels.
     * Called during login to ensure users have access to universal channels.
     */
    @Transactional
    public void autoJoinPublicChannels(User user) {
        List<Channel> publicChannels = channelRepository.findByChannelTypeAndActiveTrue(ChannelType.PUBLIC);
        List<Channel> announcementChannels = channelRepository.findByChannelTypeAndActiveTrue(ChannelType.ANNOUNCEMENT);

        int joined = 0;
        for (Channel channel : publicChannels) {
            if (!membershipRepository.existsByUserIdAndChannelIdAndActiveTrue(user.getId(), channel.getId())) {
                addMember(channel, user, false, false);
                joined++;
            }
        }
        for (Channel channel : announcementChannels) {
            if (!membershipRepository.existsByUserIdAndChannelIdAndActiveTrue(user.getId(), channel.getId())) {
                addMember(channel, user, false, false);
                joined++;
            }
        }

        if (joined > 0) {
            log.info("Auto-joined user {} to {} public/announcement channels", user.getUsername(), joined);
        }
    }

    /**
     * Sync all existing users to public and announcement channels.
     * Admin action to ensure all users have access to universal channels.
     * @return number of users processed
     */
    @Transactional
    public int syncAllUsersToPublicChannels() {
        List<User> allUsers = userRepository.findByActiveTrue();
        List<Channel> publicChannels = channelRepository.findByChannelTypeAndActiveTrue(ChannelType.PUBLIC);
        List<Channel> announcementChannels = channelRepository.findByChannelTypeAndActiveTrue(ChannelType.ANNOUNCEMENT);

        int usersProcessed = 0;
        for (User user : allUsers) {
            int joined = 0;
            for (Channel channel : publicChannels) {
                if (!membershipRepository.existsByUserIdAndChannelIdAndActiveTrue(user.getId(), channel.getId())) {
                    addMember(channel, user, false, false);
                    joined++;
                }
            }
            for (Channel channel : announcementChannels) {
                if (!membershipRepository.existsByUserIdAndChannelIdAndActiveTrue(user.getId(), channel.getId())) {
                    addMember(channel, user, false, false);
                    joined++;
                }
            }
            if (joined > 0) {
                usersProcessed++;
                log.debug("Added user {} to {} channels", user.getUsername(), joined);
            }
        }

        log.info("Synced {} users to public/announcement channels", usersProcessed);
        return usersProcessed;
    }
}
