package org.sep490.backend.module.groupquest.controller;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.groupquest.dto.ws.GroupCommand;
import org.sep490.backend.module.groupquest.dto.ws.LocationMessage;
import org.sep490.backend.module.groupquest.service.inter.GroupParticipantService;
import org.sep490.backend.module.groupquest.service.inter.GroupService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class LocationWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final GroupService groupService;

    @MessageMapping("/group/{groupId}/location")
    public void broadcastLocation(@DestinationVariable Long groupId,
                                  @Payload LocationMessage message,
                                  Principal principal) {

        if (principal instanceof JwtAuthenticationToken jwtAuthToken) {
            Jwt jwt = (Jwt) jwtAuthToken.getPrincipal();

            Object claimValue = jwt.getClaim("custom_internal_user_id");

            if (claimValue instanceof Number numberValue) {
                Long currentUserId = numberValue.longValue();
                message.setUserId(currentUserId);
            }
        }

        message.setTimestamp(LocalDateTime.now());
        messagingTemplate.convertAndSend("/topic/group/" + groupId, message);
    }

    @MessageMapping("/group/{groupId}/command")
    public void handleGroupCommand(@DestinationVariable Long groupId,
                                   @Payload GroupCommand command,
                                   Principal principal) {

        if (principal instanceof JwtAuthenticationToken jwtAuthToken) {
            Jwt jwt = (Jwt) jwtAuthToken.getPrincipal();
            Object claimValue = jwt.getClaim("custom_internal_user_id");

            if (claimValue instanceof Number numberValue) {
                Long currentUserId = numberValue.longValue();

                boolean isLeader = groupService.isLeader(currentUserId, groupId);

                if (isLeader && "STOP_LOCATION".equals(command.getAction())) {
                    messagingTemplate.convertAndSend("/topic/group/" + groupId + "/commands", command);
                }
            }
        }
    }
}
