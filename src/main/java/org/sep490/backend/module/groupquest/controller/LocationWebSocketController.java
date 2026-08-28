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

    private static final String INTERNAL_USER_ID_CLAIM = "internal_id";

    private final SimpMessagingTemplate messagingTemplate;
    private final GroupService groupService;

    @MessageMapping("/group/{groupId}/location")
    public void broadcastLocation(@DestinationVariable Long groupId,
                                  @Payload LocationMessage message,
                                  Principal principal) {

        Long currentUserId = readInternalUserId(principal);

        if (currentUserId != null) {
            message.setUserId(currentUserId);
        }

        message.setTimestamp(LocalDateTime.now());
        messagingTemplate.convertAndSend("/topic/group/" + groupId, message);
    }

    @MessageMapping("/group/{groupId}/command")
    public void handleGroupCommand(@DestinationVariable Long groupId,
                                   @Payload GroupCommand command,
                                   Principal principal) {

        Long currentUserId = readInternalUserId(principal);

        if (currentUserId == null) {
            return;
        }

        boolean isLeader = groupService.isLeader(currentUserId, groupId);

        if (isLeader && "STOP_LOCATION".equals(command.getAction())) {
            messagingTemplate.convertAndSend("/topic/group/" + groupId + "/commands", command);
        }
    }

    /**
     * Claim đúng là "internal_id" (AuthServiceImpl ghi attribute này lên
     * Keycloak). handleGroupCommand trước đây đọc "custom_internal_user_id" —
     * claim không tồn tại, nên lệnh STOP_LOCATION của trưởng nhóm không bao giờ
     * được broadcast.
     *
     * Attribute của Keycloak là chuỗi, và tuỳ cấu hình protocol mapper mà claim
     * ra dạng số hay chuỗi, nên chấp nhận cả hai thay vì chỉ instanceof Number.
     */
    private Long readInternalUserId(Principal principal) {
        if (!(principal instanceof JwtAuthenticationToken jwtAuthToken)) {
            return null;
        }

        if (!(jwtAuthToken.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        Object claimValue = jwt.getClaim(INTERNAL_USER_ID_CLAIM);

        if (claimValue instanceof Number numberValue) {
            return numberValue.longValue();
        }

        if (claimValue instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }
}
