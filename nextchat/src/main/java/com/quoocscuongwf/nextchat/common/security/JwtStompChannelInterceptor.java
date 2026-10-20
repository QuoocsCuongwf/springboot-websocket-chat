package com.quoocscuongwf.nextchat.common.security;

import io.jsonwebtoken.JwtException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import com.quoocscuongwf.nextchat.room.ConversationParticipantRepository;
import com.quoocscuongwf.nextchat.user.UserRepository;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JwtStompChannelInterceptor implements ChannelInterceptor {
    private static final Pattern ROOM_DESTINATION = Pattern.compile("^/topic/room\\.(\\d+)$");
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final ConversationParticipantRepository participantRepository;

    public JwtStompChannelInterceptor(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            UserRepository userRepository,
            ConversationParticipantRepository participantRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.participantRepository = participantRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = token(accessor.getFirstNativeHeader("Authorization"));
            if (token == null) {
                token = token(accessor.getFirstNativeHeader("access-token"));
            }
            if (token == null) {
                throw new IllegalArgumentException("A valid JWT is required to connect");
            }
            try {
                String username = jwtService.username(token);
                UserDetails user = userDetailsService.loadUserByUsername(username);
                if (!jwtService.isValid(token, user)) {
                    throw new IllegalArgumentException("A valid JWT is required to connect");
                }
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities());
                accessor.setUser(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                throw new IllegalArgumentException("A valid JWT is required to connect", ex);
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Matcher matcher = ROOM_DESTINATION.matcher(
                    accessor.getDestination() == null ? "" : accessor.getDestination());
            if (matcher.matches() && !isRoomMember(matcher.group(1), accessor.getUser())) {
                throw new IllegalArgumentException("You are not a participant in this room");
            }
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    private boolean isRoomMember(String roomId, java.security.Principal principal) {
        if (!(principal instanceof Authentication authentication)) {
            return false;
        }
        return userRepository.findByUsername(authentication.getName())
                .filter(user -> user.isActive() && !user.isDeleted())
                .map(user -> participantRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(
                        Long.parseLong(roomId), user.getId()))
                .orElse(false);
    }

    private String token(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
    }
}
