package com.quoocscuongwf.nextchat.room;

import com.quoocscuongwf.nextchat.room.dto.MessageDTO;
import com.quoocscuongwf.nextchat.room.dto.SendMessageRequest;
import com.quoocscuongwf.nextchat.user.User;
import com.quoocscuongwf.nextchat.user.UserRepository;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class RoomWebSocketController {
    private final RoomService roomService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public RoomWebSocketController(
            RoomService roomService,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/room/{roomId}")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Valid @Payload SendMessageRequest request,
            Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Long userId = userRepository.findByUsername(principal.getName())
                .filter(user -> user.isActive() && !user.isDeleted())
                .map(User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        MessageDTO message = roomService.sendMessage(
                userId, roomId, request.content(), request.messageType(), request.replyToMessageId());
        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
    }
}
