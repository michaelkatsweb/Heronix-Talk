package com.heronix.talk.service;

import com.heronix.talk.model.domain.Channel;
import com.heronix.talk.model.domain.Message;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.dto.MessageDTO;
import com.heronix.talk.model.dto.SendMessageRequest;
import com.heronix.talk.model.enums.MessageStatus;
import com.heronix.talk.model.enums.MessageType;
import com.heronix.talk.model.enums.SyncStatus;
import com.heronix.talk.repository.ChannelMembershipRepository;
import com.heronix.talk.repository.MessageRepository;
import com.heronix.talk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for message operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChannelMembershipRepository membershipRepository;
    private final ChannelService channelService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // Pattern to match @mentions (e.g., @John_Doe or @john.doe)
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w.]+)");

    public Optional<Message> findById(Long id) {
        return messageRepository.findById(id);
    }

    public Optional<Message> findByUuid(String uuid) {
        return messageRepository.findByMessageUuid(uuid);
    }

    public List<MessageDTO> getChannelMessages(Long channelId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Message> messages = messageRepository.findByChannelIdOrderByTimestampDesc(channelId, pageable);
        return messages.getContent().stream()
                .map(MessageDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<MessageDTO> getChannelMessagesAsc(Long channelId) {
        return messageRepository.findByChannelIdOrderByTimestampAsc(channelId).stream()
                .map(MessageDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<MessageDTO> getMessagesSince(Long channelId, LocalDateTime since) {
        return messageRepository.findByChannelIdAndTimestampAfter(channelId, since).stream()
                .map(MessageDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<MessageDTO> getMessagesAfter(Long channelId, Long afterMessageId) {
        return messageRepository.findByChannelIdAndIdGreaterThan(channelId, afterMessageId).stream()
                .map(MessageDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<MessageDTO> getPinnedMessages(Long channelId) {
        return messageRepository.findPinnedMessagesByChannelId(channelId).stream()
                .map(MessageDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<MessageDTO> getReplies(Long messageId) {
        return messageRepository.findReplies(messageId).stream()
                .map(MessageDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<MessageDTO> searchInChannel(Long channelId, String term, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return messageRepository.searchInChannel(channelId, term, pageable).getContent().stream()
                .map(MessageDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<MessageDTO> searchAll(String term, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return messageRepository.searchAll(term, pageable).getContent().stream()
                .map(MessageDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public Message sendMessage(SendMessageRequest request, User sender, Channel channel) {
        log.info("Sending message to channel '{}' (id={}) from user '{}' (id={})",
                channel.getName(), channel.getId(), sender.getUsername(), sender.getId());
        log.debug("Message content: {}", request.getContent());

        // Check for duplicate client ID
        if (request.getClientId() != null) {
            boolean isDuplicate = messageRepository.existsByClientId(request.getClientId());
            if (isDuplicate) {
                log.warn("Duplicate message with clientId: {} - rejecting", request.getClientId());
                return null;
            }
            log.debug("ClientId {} is unique, proceeding", request.getClientId());
        }

        // Determine message type
        MessageType messageType = request.getMessageType() != null ? request.getMessageType() : MessageType.TEXT;

        // Look up reply target before building message
        Message replyTo = null;
        if (request.getReplyToId() != null) {
            replyTo = messageRepository.findById(request.getReplyToId()).orElse(null);
            if (replyTo != null) {
                messageType = MessageType.REPLY;
            }
        }

        Message message = Message.builder()
                .channel(channel)
                .sender(sender)
                .content(request.getContent())
                .messageType(messageType)
                .status(MessageStatus.SENT)
                .timestamp(LocalDateTime.now())
                .clientId(request.getClientId())
                .syncStatus(SyncStatus.LOCAL_ONLY)
                .replyTo(replyTo)
                .build();

        // Handle mentions - parse from content if not provided
        List<Long> mentionedUserIds = request.getMentionedUserIds();
        if (mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            mentionedUserIds = parseMentionsFromContent(request.getContent(), channel.getId());
        }
        if (mentionedUserIds != null && !mentionedUserIds.isEmpty()) {
            try {
                message.setMentions(objectMapper.writeValueAsString(mentionedUserIds));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize mentions", e);
            }
        }

        // Handle attachment
        if (request.getAttachmentName() != null) {
            message.setAttachmentName(request.getAttachmentName());
            message.setAttachmentType(request.getAttachmentType());
            message.setAttachmentSize(request.getAttachmentSize());
            if (request.getMessageType() == null) {
                message.setMessageType(MessageType.FILE);
            }
        }

        Message savedMessage = messageRepository.save(message);
        log.info("Message persisted to database: id={}, uuid={}", savedMessage.getId(), savedMessage.getMessageUuid());

        // Update reply count if this is a reply
        if (savedMessage.getReplyTo() != null) {
            messageRepository.incrementReplyCount(savedMessage.getReplyTo().getId());
            log.debug("Incremented reply count for message {}", savedMessage.getReplyTo().getId());
        }

        // Update channel
        channelService.updateLastMessageTime(channel.getId());
        log.debug("Updated last message time for channel {}", channel.getId());

        // Increment unread count for other members
        membershipRepository.incrementUnreadForChannel(channel.getId(), sender.getId());
        log.debug("Incremented unread count for channel {} members (excluding sender {})", channel.getId(), sender.getId());

        log.info("Message sent successfully: uuid={} to channel '{}' (id={})",
                savedMessage.getMessageUuid(), channel.getName(), channel.getId());
        return savedMessage;
    }

    @Transactional
    public Message editMessage(Long messageId, String newContent, Long editorId) {
        return messageRepository.findById(messageId)
                .filter(m -> m.getSender().getId().equals(editorId))
                .map(message -> {
                    message.setContent(newContent);
                    message.markAsEdited();
                    log.info("Message {} edited", message.getMessageUuid());
                    return messageRepository.save(message);
                })
                .orElse(null);
    }

    @Transactional
    public boolean deleteMessage(Long messageId, Long deleterId) {
        return messageRepository.findById(messageId)
                .filter(m -> m.getSender().getId().equals(deleterId))
                .map(message -> {
                    message.markAsDeleted();
                    messageRepository.save(message);
                    log.info("Message {} deleted", message.getMessageUuid());
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void pinMessage(Long messageId, boolean pinned) {
        messageRepository.updatePinned(messageId, pinned);
        log.info("Message {} {}", messageId, pinned ? "pinned" : "unpinned");
    }

    @Transactional
    public void markImportant(Long messageId, boolean important) {
        messageRepository.findById(messageId).ifPresent(message -> {
            message.setImportant(important);
            messageRepository.save(message);
        });
    }

    /**
     * Add a reaction to a message.
     * Reactions are stored as JSON: {"thumbsUp": [1,2,3], "heart": [5,6]}
     * @return Updated reactions map, or null if message not found
     */
    @Transactional
    public Map<String, List<Long>> addReaction(Long messageId, String emoji, Long userId) {
        return messageRepository.findById(messageId)
                .map(message -> {
                    Map<String, List<Long>> reactions = parseReactions(message.getReactions());

                    // Add user to the emoji's list (if not already present)
                    reactions.computeIfAbsent(emoji, k -> new ArrayList<>());
                    if (!reactions.get(emoji).contains(userId)) {
                        reactions.get(emoji).add(userId);
                    }

                    // Save back as JSON
                    message.setReactions(serializeReactions(reactions));
                    messageRepository.save(message);
                    log.debug("Reaction {} added to message {} by user {}", emoji, messageId, userId);
                    return reactions;
                })
                .orElse(null);
    }

    /**
     * Remove a reaction from a message.
     * @return Updated reactions map, or null if message not found
     */
    @Transactional
    public Map<String, List<Long>> removeReaction(Long messageId, String emoji, Long userId) {
        return messageRepository.findById(messageId)
                .map(message -> {
                    Map<String, List<Long>> reactions = parseReactions(message.getReactions());

                    // Remove user from the emoji's list
                    if (reactions.containsKey(emoji)) {
                        reactions.get(emoji).remove(userId);
                        // Remove emoji if no users left
                        if (reactions.get(emoji).isEmpty()) {
                            reactions.remove(emoji);
                        }
                    }

                    // Save back as JSON
                    message.setReactions(serializeReactions(reactions));
                    messageRepository.save(message);
                    log.debug("Reaction {} removed from message {} by user {}", emoji, messageId, userId);
                    return reactions;
                })
                .orElse(null);
    }

    /**
     * Toggle a reaction (add if not present, remove if present)
     * @return Updated reactions map
     */
    @Transactional
    public Map<String, List<Long>> toggleReaction(Long messageId, String emoji, Long userId) {
        return messageRepository.findById(messageId)
                .map(message -> {
                    Map<String, List<Long>> reactions = parseReactions(message.getReactions());

                    reactions.computeIfAbsent(emoji, k -> new ArrayList<>());
                    List<Long> userIds = reactions.get(emoji);

                    if (userIds.contains(userId)) {
                        userIds.remove(userId);
                        if (userIds.isEmpty()) {
                            reactions.remove(emoji);
                        }
                        log.debug("Reaction {} removed from message {} by user {}", emoji, messageId, userId);
                    } else {
                        userIds.add(userId);
                        log.debug("Reaction {} added to message {} by user {}", emoji, messageId, userId);
                    }

                    message.setReactions(serializeReactions(reactions));
                    messageRepository.save(message);
                    return reactions;
                })
                .orElse(null);
    }

    /**
     * Get reactions for a message
     */
    public Map<String, List<Long>> getReactions(Long messageId) {
        return messageRepository.findById(messageId)
                .map(message -> parseReactions(message.getReactions()))
                .orElse(new HashMap<>());
    }

    /**
     * Parse reactions JSON string to map
     */
    private Map<String, List<Long>> parseReactions(String reactionsJson) {
        if (reactionsJson == null || reactionsJson.isEmpty()) {
            return new HashMap<>();
        }

        // Handle legacy format (emoji:userId;emoji:userId;)
        if (!reactionsJson.startsWith("{")) {
            return migrateLegacyReactions(reactionsJson);
        }

        try {
            return objectMapper.readValue(reactionsJson,
                    new TypeReference<Map<String, List<Long>>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse reactions JSON: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Migrate legacy reaction format to new JSON format
     */
    private Map<String, List<Long>> migrateLegacyReactions(String legacy) {
        Map<String, List<Long>> reactions = new HashMap<>();
        if (legacy == null || legacy.isEmpty()) {
            return reactions;
        }

        String[] parts = legacy.split(";");
        for (String part : parts) {
            if (part.contains(":")) {
                String[] emojiUser = part.split(":");
                if (emojiUser.length == 2) {
                    String emoji = emojiUser[0];
                    try {
                        Long userId = Long.parseLong(emojiUser[1]);
                        reactions.computeIfAbsent(emoji, k -> new ArrayList<>());
                        if (!reactions.get(emoji).contains(userId)) {
                            reactions.get(emoji).add(userId);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return reactions;
    }

    /**
     * Serialize reactions map to JSON string
     */
    private String serializeReactions(Map<String, List<Long>> reactions) {
        if (reactions == null || reactions.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(reactions);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize reactions: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public void markAsRead(Long channelId, Long userId, Long messageId) {
        membershipRepository.markAsRead(userId, channelId, messageId, LocalDateTime.now());

        // Also update the message's read receipts
        addReadReceipt(messageId, userId);
    }

    /**
     * Add a read receipt to a message
     */
    @Transactional
    public void addReadReceipt(Long messageId, Long userId) {
        messageRepository.findById(messageId).ifPresent(message -> {
            Map<String, Object> receipts = parseReadReceipts(message.getReadReceipts());
            receipts.put(userId.toString(), LocalDateTime.now().toString());
            message.setReadReceipts(serializeReadReceipts(receipts));
            messageRepository.save(message);
        });
    }

    /**
     * Get read receipts for a message
     */
    public Map<Long, LocalDateTime> getReadReceipts(Long messageId) {
        return messageRepository.findById(messageId)
                .map(message -> {
                    Map<String, Object> receipts = parseReadReceipts(message.getReadReceipts());
                    Map<Long, LocalDateTime> result = new java.util.HashMap<>();
                    for (Map.Entry<String, Object> entry : receipts.entrySet()) {
                        try {
                            Long uid = Long.parseLong(entry.getKey());
                            LocalDateTime readAt = LocalDateTime.parse(entry.getValue().toString());
                            result.put(uid, readAt);
                        } catch (Exception e) {
                            // Skip invalid entries
                        }
                    }
                    return result;
                })
                .orElse(new java.util.HashMap<>());
    }

    /**
     * Check if a message has been read by a user
     */
    public boolean isReadBy(Long messageId, Long userId) {
        return getReadReceipts(messageId).containsKey(userId);
    }

    /**
     * Get the count of users who have read a message
     */
    public int getReadCount(Long messageId) {
        return getReadReceipts(messageId).size();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseReadReceipts(String json) {
        if (json == null || json.isEmpty()) {
            return new java.util.HashMap<>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new java.util.HashMap<>();
        }
    }

    private String serializeReadReceipts(Map<String, Object> receipts) {
        try {
            return objectMapper.writeValueAsString(receipts);
        } catch (Exception e) {
            return "{}";
        }
    }

    public long getUnreadCount(Long channelId, Long afterMessageId) {
        return messageRepository.countUnreadInChannel(channelId, afterMessageId);
    }

    public Optional<Long> getLastMessageId(Long channelId) {
        return messageRepository.findLastMessageIdInChannel(channelId);
    }

    // ========================================================================
    // MENTION PARSING
    // ========================================================================

    /**
     * Parse @mentions from message content and resolve to user IDs
     * Supports @username, @firstname.lastname patterns
     */
    public List<Long> parseMentionsFromContent(String content, Long channelId) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> mentionedUserIds = new ArrayList<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);

        while (matcher.find()) {
            String username = matcher.group(1);

            // Try to find user by username
            userRepository.findByUsername(username).ifPresent(user -> {
                // Verify user is a member of the channel (optional - could mention anyone)
                if (channelService.isMember(channelId, user.getId())) {
                    mentionedUserIds.add(user.getId());
                }
            });

            // Also try by employee ID
            if (mentionedUserIds.isEmpty()) {
                userRepository.findByEmployeeId(username).ifPresent(user -> {
                    if (channelService.isMember(channelId, user.getId())) {
                        mentionedUserIds.add(user.getId());
                    }
                });
            }
        }

        return mentionedUserIds.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Get list of mentioned user IDs from a message
     */
    public List<Long> getMentionedUserIds(Long messageId) {
        return messageRepository.findById(messageId)
                .map(message -> {
                    String mentions = message.getMentions();
                    if (mentions == null || mentions.isEmpty()) {
                        return Collections.<Long>emptyList();
                    }
                    try {
                        return objectMapper.readValue(mentions, new TypeReference<List<Long>>() {});
                    } catch (Exception e) {
                        // Try parsing as simple array string [1, 2, 3]
                        try {
                            return Arrays.stream(mentions.replaceAll("[\\[\\]\\s]", "").split(","))
                                    .filter(s -> !s.isEmpty())
                                    .map(Long::parseLong)
                                    .collect(Collectors.toList());
                        } catch (Exception ex) {
                            return Collections.<Long>emptyList();
                        }
                    }
                })
                .orElse(Collections.emptyList());
    }

    /**
     * Check if a user is mentioned in a message
     */
    public boolean isMentioned(Long messageId, Long userId) {
        return getMentionedUserIds(messageId).contains(userId);
    }

    public long getMessageCount(Long channelId) {
        return messageRepository.countByChannelId(channelId);
    }

    public List<Message> getMessagesNeedingSync() {
        return messageRepository.findNeedingSync();
    }
}
