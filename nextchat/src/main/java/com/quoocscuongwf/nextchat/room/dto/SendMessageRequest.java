package com.quoocscuongwf.nextchat.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank @Size(max = 500000) String content,
        @Size(max = 10) String messageType,
        Long replyToMessageId
) {
}
