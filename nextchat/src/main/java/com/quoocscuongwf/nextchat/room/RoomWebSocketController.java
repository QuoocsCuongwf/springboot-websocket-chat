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
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class RoomWebSocketController {
    private final RoomService roomService;
    private final UserRepository userRepository;

    public RoomWebSocketController(RoomService roomService, UserRepository userRepository) {
        this.roomService = roomService;
        this.userRepository = userRepository;
    }

    @MessageMapping("/room.{roomId}")
    @SendTo("/topic/room.{roomId}")
    public MessageDTO sendMessage(
            @DestinationVariable Long roomId,
            @Valid @Payload SendMessageRequest request,
            Principal principal) {
        Long userId = userRepository.findByUsername(principal.getName())
                .filter(user -> user.isActive() && !user.isDeleted())
                .map(User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        return roomService.sendMessage(
                userId, roomId, request.content(), request.messageType(), request.replyToMessageId());
    }
}
