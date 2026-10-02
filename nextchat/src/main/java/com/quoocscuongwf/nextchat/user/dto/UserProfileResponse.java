package com.quoocscuongwf.nextchat.user.dto;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String fullName) {
}
