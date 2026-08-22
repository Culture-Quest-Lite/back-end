package org.sep490.backend.config.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    public WebSocketAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authorization = accessor.getNativeHeader("Authorization");

            if (authorization != null && !authorization.isEmpty()) {
                String bearerToken = authorization.get(0);

                if (bearerToken.startsWith("Bearer ")) {
                    String token = bearerToken.substring(7);

                    try {
                        Jwt jwt = jwtDecoder.decode(token);

                        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
                        AbstractAuthenticationToken authentication = converter.convert(jwt);

                        accessor.setUser(authentication);

                    } catch (JwtException e) {
                        throw new IllegalArgumentException("Token Keycloak không hợp lệ hoặc đã hết hạn", e);
                    }
                }
            } else {
                throw new IllegalArgumentException("Từ chối kết nối: Thiếu header Authorization");
            }
        }
        return message;
    }
}
