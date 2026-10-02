package com.quoocscuongwf.nextchat.room;

import com.quoocscuongwf.nextchat.common.dto.ApiResponse;
import com.quoocscuongwf.nextchat.room.dto.CreateGroupRoomRequest;
import com.quoocscuongwf.nextchat.room.dto.DirectRoomRequest;
import com.quoocscuongwf.nextchat.room.dto.MessageDTO;
import com.quoocscuongwf.nextchat.room.dto.RoomDTO;
import com.quoocscuongwf.nextchat.user.User;
import com.quoocscuongwf.nextchat.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {
    private final RoomService roomService;
    private final UserRepository userRepository;

    public RoomController(RoomService roomService, UserRepository userRepository) {
        this.roomService = roomService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ApiResponse<List<RoomDTO>> getUserRooms(Authentication authentication) {
        Long currentUserId = getCurrentUserId(authentication);
        List<RoomDTO> rooms = roomService.getUserRooms(currentUserId);
        return ApiResponse.success("Rooms retrieved successfully", rooms);
    }

    @PostMapping("/direct")
    public ApiResponse<RoomDTO> getOrCreateDirectRoom(
            @Valid @RequestBody DirectRoomRequest request,
            Authentication authentication) {
        Long currentUserId = getCurrentUserId(authentication);
        RoomDTO room = roomService.getOrCreateDirectRoom(currentUserId, request.targetUserId());
        return ApiResponse.success("Direct room retrieved or created", room);
    }

    @PostMapping("/group")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoomDTO> createGroupRoom(
            @Valid @RequestBody CreateGroupRoomRequest request,
            Authentication authentication) {
        Long currentUserId = getCurrentUserId(authentication);
        RoomDTO room = roomService.createGroupRoom(currentUserId, request.groupName(), request.memberIds());
        return ApiResponse.success("Group room created successfully", room);
    }

    @GetMapping("/{roomId}/messages")
    public ApiResponse<Page<MessageDTO>> getRoomMessages(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long currentUserId = getCurrentUserId(authentication);
        Page<MessageDTO> messagePage = roomService.getRoomMessages(currentUserId, roomId, page, size);
        return ApiResponse.success("Messages retrieved successfully", messagePage);
    }

    private Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails details)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return userRepository.findByUsername(details.getUsername())
                .filter(u -> u.isActive() && !u.isDeleted())
                .map(User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
