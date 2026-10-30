package com.quoocscuongwf.nextchat.security;

import com.quoocscuongwf.nextchat.common.security.JwtService;
import com.quoocscuongwf.nextchat.common.security.JwtStompChannelInterceptor;
import com.quoocscuongwf.nextchat.room.ConversationParticipantRepository;
import com.quoocscuongwf.nextchat.user.User;
import com.quoocscuongwf.nextchat.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JwtStompChannelInterceptorTest {
    private JwtService jwtService;
    private UserDetailsService userDetailsService;
    private UserRepository userRepository;
    private ConversationParticipantRepository participantRepository;
    private JwtStompChannelInterceptor interceptor;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        userDetailsService = mock(UserDetailsService.class);
        userRepository = mock(UserRepository.class);
        participantRepository = mock(ConversationParticipantRepository.class);
        interceptor = new JwtStompChannelInterceptor(
                jwtService, userDetailsService, userRepository, participantRepository);
        userDetails = org.springframework.security.core.userdetails.User
                .withUsername("alice").password("ignored").authorities("ROLE_USER").build();
        when(jwtService.username("token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(jwtService.isValid("token", userDetails)).thenReturn(true);
    }

    @Test
    void connectWithValidBearerTokenSetsAuthenticatedPrincipal() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer token");

        Message<?> result = interceptor.preSend(message(accessor), mock());

        StompHeaderAccessor connected = StompHeaderAccessor.wrap(result);
        assertThat(connected.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(connected.getUser().getName()).isEqualTo("alice");
    }

    @Test
    void connectWithoutTokenIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A valid JWT is required to connect");
    }

    @Test
    void subscribeToRoomRequiresActiveMembership() {
        User member = mock(User.class);
        when(member.isActive()).thenReturn(true);
        when(member.isDeleted()).thenReturn(false);
        when(member.getId()).thenReturn(42L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(member));
        when(participantRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(7L, 42L))
                .thenReturn(true);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/room/7");
        accessor.setUser(new UsernamePasswordAuthenticationToken(userDetails, null));

        interceptor.preSend(message(accessor), mock());

        verify(participantRepository).existsByConversationIdAndUserIdAndLeftAtIsNull(7L, 42L);
    }

    @Test
    void subscribeToRoomWithoutMembershipIsRejected() {
        User member = mock(User.class);
        when(member.isActive()).thenReturn(true);
        when(member.isDeleted()).thenReturn(false);
        when(member.getId()).thenReturn(42L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(member));
        when(participantRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(7L, 42L))
                .thenReturn(false);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/room/7");
        accessor.setUser(new UsernamePasswordAuthenticationToken(userDetails, null));

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You are not a participant in this room");
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
