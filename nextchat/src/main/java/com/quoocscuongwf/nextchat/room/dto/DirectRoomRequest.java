package com.quoocscuongwf.nextchat.room.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DirectRoomRequest(
        @NotNull @Positive Long targetUserId
) {
}
