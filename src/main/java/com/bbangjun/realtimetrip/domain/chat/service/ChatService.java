package com.bbangjun.realtimetrip.domain.chat.service;

import com.bbangjun.realtimetrip.domain.chat.dto.ChatMessageDto;
import com.bbangjun.realtimetrip.domain.chat.entity.ChatMessage;
import com.bbangjun.realtimetrip.domain.chat.entity.ChatRoom;
import com.bbangjun.realtimetrip.domain.chat.repository.ChatMessageRepository;
import com.bbangjun.realtimetrip.domain.chat.repository.ChatRoomRepository;
import com.bbangjun.realtimetrip.domain.user.entity.User;
import com.bbangjun.realtimetrip.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RedisPublisherService redisPublisherService;
    private final RedisSubscriberService redisSubscriberService;
    private final RedisMessageListenerContainer redisMessageListener;

    @Transactional
    public void sendMessage(ChatMessageDto chatMessageDto) {
        if (ChatMessageDto.MessageType.TALK.equals(chatMessageDto.getMessageType())) {

            Optional<User> optionalUser = userRepository.findById(chatMessageDto.getUserId());
            User user = optionalUser.get();

            ChatRoom chatRoom = chatRoomRepository.findByRoomId(chatMessageDto.getRoomId());

            //채팅 생성 및 저장
            ChatMessage newChatMessage = ChatMessage.builder()
                    .messageType(ChatMessage.MessageType.TALK)
                    .chatRoom(chatRoom)
                    .user(user)
                    .message(chatMessageDto.getMessage())
                    .sendTime(LocalDateTime.now())
                    .build();

            chatMessageRepository.save(newChatMessage);

            ChannelTopic topic = new ChannelTopic(chatMessageDto.getRoomId());

            // ChatMessageRequest에 유저정보, 현재시간 저장
            chatMessageDto.setId(newChatMessage.getId());
            chatMessageDto.setNickName(user.getNickname());
            chatMessageDto.setUserId(user.getUserId());
            chatMessageDto.setSendTime(LocalDateTime.now());

            redisPublisherService.publish(topic, chatMessageDto);
        }
    }

    public void enterUser(ChatMessageDto chatMessageDto) {
        if (ChatMessageDto.MessageType.ENTER.equals(chatMessageDto.getMessageType())) {

            ChannelTopic topic = new ChannelTopic(chatMessageDto.getRoomId());
            redisMessageListener.addMessageListener(redisSubscriberService, topic);
            chatMessageDto.setNickName(userRepository.findById(chatMessageDto.getUserId()).get().getNickname());
            chatMessageDto.setSendTime(LocalDateTime.now());
            chatMessageDto.setMessage(chatMessageDto.getNickName() + "님이 입장하셨습니다.");

            // Websocket에 발행된 메시지를 redis로 발행한다(publish)
            redisPublisherService.publish(topic, chatMessageDto);
        }
    }
}
