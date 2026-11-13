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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {
    private static final Path ATTACHMENT_DIR = Paths.get("uploads", "messages");
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

    @PostMapping("/{roomId}/read")
    public ApiResponse<Void> markRoomAsRead(
            @PathVariable Long roomId,
            Authentication authentication) {
        Long currentUserId = getCurrentUserId(authentication);
        roomService.markRoomAsRead(currentUserId, roomId);
        return ApiResponse.success("Room marked as read", null);
    }

    @PostMapping("/attachments")
    public ApiResponse<Map<String, String>> uploadAttachment(
            @RequestBody Map<String, String> payload) {
        String imageInput = payload != null ? payload.get("image") : null;
        if (imageInput == null || !imageInput.startsWith("data:image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image payload");
        }
        int commaIdx = imageInput.indexOf(',');
        if (commaIdx <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed base64 image");
        }
        String header = imageInput.substring(0, commaIdx).toLowerCase();
        String ext = ".jpg";
        if (header.contains("png")) {
            ext = ".png";
        } else if (header.contains("webp")) {
            ext = ".webp";
        } else if (header.contains("gif")) {
            ext = ".gif";
        }
        try {
            byte[] imageBytes = Base64.getDecoder().decode(imageInput.substring(commaIdx + 1));
            Files.createDirectories(ATTACHMENT_DIR);
            String fileName = "msg_" + UUID.randomUUID().toString().replace("-", "") + ext;
            Path file = ATTACHMENT_DIR.resolve(fileName);
            Files.write(file, imageBytes);
            String url = "/api/v1/rooms/attachments/" + fileName;
            return ApiResponse.success("Image uploaded", Map.of("url", url));
        } catch (IOException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to save image attachment");
        }
    }

    @GetMapping("/attachments/{fileName}")
    public ResponseEntity<byte[]> getAttachment(@PathVariable String fileName) {
        if (fileName == null || !fileName.matches("^msg_[a-zA-Z0-9]+\\.(jpg|png|webp|gif)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attachment file name");
        }
        Path file = ATTACHMENT_DIR.resolve(fileName);
        if (!Files.exists(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found");
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            MediaType mediaType = MediaType.IMAGE_JPEG;
            if (fileName.endsWith(".png")) {
                mediaType = MediaType.IMAGE_PNG;
            } else if (fileName.endsWith(".gif")) {
                mediaType = MediaType.IMAGE_GIF;
            } else if (fileName.endsWith(".webp")) {
                mediaType = MediaType.parseMediaType("image/webp");
            }
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(bytes);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read attachment");
        }
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
