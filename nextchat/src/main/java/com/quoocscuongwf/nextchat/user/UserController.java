package com.quoocscuongwf.nextchat.user;

import com.quoocscuongwf.nextchat.common.dto.ApiResponse;
import com.quoocscuongwf.nextchat.user.dto.UserProfileResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails details)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        User user = users.findByUsername(details.getUsername())
                .filter(found -> found.isActive() && !found.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return ApiResponse.success("User profile loaded",
                new UserProfileResponse(user.getId(), user.getUsername(),
                        user.getEmail(), user.getFullName()));
    }
}
