package org.sep490.backend.module.groupquest.controller;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.groupquest.dto.LocationMessage;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class LocationWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/group/{groupId}/location")
    public void broadcastLocation(@DestinationVariable Long groupId,
                                  @Payload LocationMessage message,
                                  Principal principal) {

        Long currentUserId = Long.valueOf(principal.getName());
        message.setUserId(currentUserId);

        message.setTimestamp(LocalDateTime.now());
        messagingTemplate.convertAndSend("/topic/group/" + groupId, message);
    }
}
