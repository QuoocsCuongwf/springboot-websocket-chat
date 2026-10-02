package com.quoocscuongwf.nextchat.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateGroupRoomRequest(
        @NotBlank @Size(min = 1, max = 150) String groupName,
        @NotEmpty List<@NotNull @Positive Long> memberIds
) {
}
