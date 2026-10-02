package com.quoocscuongwf.nextchat.user;

import com.quoocscuongwf.nextchat.common.dto.ApiResponse;
import com.quoocscuongwf.nextchat.user.dto.UserDTO;
import com.quoocscuongwf.nextchat.user.dto.UserProfileDTO;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
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
