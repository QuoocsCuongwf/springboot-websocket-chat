package com.quoocscuongwf.nextchat.auth.dto;

import com.quoocscuongwf.nextchat.user.dto.UserDTO;

public record JwtResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn,
        UserDTO user
) {
    public JwtResponse(String accessToken, String refreshToken, Long expiresIn, UserDTO user) {
        this(accessToken, refreshToken, "Bearer", expiresIn, user);
    }

    public JwtResponse(String accessToken, String refreshToken) {
        this(accessToken, refreshToken, "Bearer", null, null);
    }
}
