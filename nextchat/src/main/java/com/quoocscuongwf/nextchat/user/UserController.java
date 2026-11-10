package com.quoocscuongwf.nextchat.user;

import com.quoocscuongwf.nextchat.common.dto.ApiResponse;
import com.quoocscuongwf.nextchat.user.dto.UserDTO;
import com.quoocscuongwf.nextchat.user.dto.UserProfileDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private static final Path AVATAR_DIR = Paths.get("uploads", "avatars");
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ApiResponse<List<UserDTO>> getAllContacts(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        List<UserDTO> contacts = userRepository.findAll().stream()
                .filter(u -> u.isActive() && !u.isDeleted())
                .map(UserDTO::fromEntity)
                .toList();
        return ApiResponse.success("Contacts loaded", contacts);
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileDTO> currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails details)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        User user = userRepository.findByUsername(details.getUsername())
                .filter(found -> found.isActive() && !found.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return ApiResponse.success("User profile loaded", UserProfileDTO.fromEntity(user));
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileDTO> updateProfile(
            @RequestBody Map<String, String> payload,
            Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails details)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        User user = userRepository.findByUsername(details.getUsername())
                .filter(found -> found.isActive() && !found.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (payload.containsKey("fullName") && payload.get("fullName") != null && !payload.get("fullName").isBlank()) {
            String fullName = payload.get("fullName").trim();
            if (fullName.length() <= 150) {
                user.setFullName(fullName);
            }
        }
        if (payload.containsKey("bio") && payload.get("bio") != null) {
            String bio = payload.get("bio").trim();
            if (bio.length() <= 255) {
                user.setBio(bio);
            }
        }
        if (payload.containsKey("avatarUrl") && payload.get("avatarUrl") != null && !payload.get("avatarUrl").isBlank()) {
            String avatarInput = payload.get("avatarUrl").trim();
            if (avatarInput.startsWith("data:image/")) {
                int commaIdx = avatarInput.indexOf(',');
                if (commaIdx > 0) {
                    try {
                        byte[] imageBytes = Base64.getDecoder().decode(avatarInput.substring(commaIdx + 1));
                        Files.createDirectories(AVATAR_DIR);
                        Path file = AVATAR_DIR.resolve("user_" + user.getId() + ".png");
                        Files.write(file, imageBytes);
                        user.setAvatarUrl("/api/v1/users/avatars/" + user.getId() + "?t=" + System.currentTimeMillis());
                    } catch (IOException | IllegalArgumentException ex) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid avatar image data");
                    }
                }
            } else if (avatarInput.length() <= 500) {
                user.setAvatarUrl(avatarInput);
            }
        }

        User saved = userRepository.save(user);
        return ApiResponse.success("Profile updated successfully", UserProfileDTO.fromEntity(saved));
    }

    @GetMapping("/avatars/{userId}")
    public ResponseEntity<byte[]> getUserAvatar(@PathVariable Long userId) {
        Path file = AVATAR_DIR.resolve("user_" + userId + ".png");
        if (!Files.exists(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar not found");
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(bytes);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read avatar");
        }
    }

    @GetMapping("/search")
    public ApiResponse<List<UserDTO>> searchUsers(
            @RequestParam(value = "q", defaultValue = "") String query,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String trimmed = query != null ? query.trim() : "";
        if (trimmed.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must not exceed 100 characters");
        }
        List<User> foundUsers;
        if (trimmed.isEmpty()) {
            foundUsers = List.of();
        } else {
            foundUsers = userRepository.searchUsers(trimmed);
        }

        List<UserDTO> dtoList = foundUsers.stream()
                .map(UserDTO::fromEntity)
                .toList();

        return ApiResponse.success("Users search results", dtoList);
    }
}
