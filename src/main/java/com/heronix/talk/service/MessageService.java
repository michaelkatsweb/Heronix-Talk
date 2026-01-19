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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
        log.debug("Sending message to channel {} from user {}", channel.getName(), sender.getUsername());

        // Check for duplicate client ID
        if (request.getClientId() != null && messageRepository.existsByClientId(request.getClientId())) {
            log.warn("Duplicate message with clientId: {}", request.getClientId());
            return null;
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

        // Handle mentions
        if (request.getMentionedUserIds() != null && !request.getMentionedUserIds().isEmpty()) {
            message.setMentions(request.getMentionedUserIds().toString());
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

        // Update reply count if this is a reply
        if (savedMessage.getReplyTo() != null) {
            messageRepository.incrementReplyCount(savedMessage.getReplyTo().getId());
        }

        // Update channel
        channelService.updateLastMessageTime(channel.getId());

        // Increment unread count for other members
        membershipRepository.incrementUnreadForChannel(channel.getId(), sender.getId());

        log.info("Message sent: {} to channel {}", savedMessage.getMessageUuid(), channel.getName());
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

    @Transactional
    public void addReaction(Long messageId, String emoji, Long userId) {
        messageRepository.findById(messageId).ifPresent(message -> {
            // Simple reaction storage - could be enhanced with proper JSON handling
            String reactions = message.getReactions();
            if (reactions == null) {
                reactions = "";
            }
            reactions += emoji + ":" + userId + ";";
            message.setReactions(reactions);
            messageRepository.save(message);
            log.debug("Reaction {} added to message {} by user {}", emoji, messageId, userId);
        });
    }

    @Transactional
    public void markAsRead(Long channelId, Long userId, Long messageId) {
        membershipRepository.markAsRead(userId, channelId, messageId, LocalDateTime.now());
    }

    public long getUnreadCount(Long channelId, Long afterMessageId) {
        return messageRepository.countUnreadInChannel(channelId, afterMessageId);
    }

    public Optional<Long> getLastMessageId(Long channelId) {
        return messageRepository.findLastMessageIdInChannel(channelId);
    }

    public long getMessageCount(Long channelId) {
        return messageRepository.countByChannelId(channelId);
    }

    public List<Message> getMessagesNeedingSync() {
        return messageRepository.findNeedingSync();
    }
}
