package org.sep490.backend.module.groupquest.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/test-ws")
@RequiredArgsConstructor
public class TestWebSocketController {

    // Inject công cụ gửi tin nhắn WebSocket của Spring
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/send-location")
    public ResponseEntity<?> sendTestLocation(@RequestParam(defaultValue = "1") Long groupId) {

        // Giả lập một gói tin vị trí giống hệt LocationMessage
        Map<String, Object> mockLocation = Map.of(
                "userId", 9999, // ID giả của hệ thống
                "displayName", "Hệ thống test",
                "latitude", 10.8231,
                "longitude", 106.6297,
                "timestamp", LocalDateTime.now().toString()
        );

        // Bắn gói tin này tới tất cả những ai đang subscribe kênh của group đó
        String destination = "/topic/group/" + groupId;
        messagingTemplate.convertAndSend(destination, mockLocation);

        return ResponseEntity.ok(Map.of(
                "status", "Thành công",
                "message", "Đã bắn dữ liệu xuống WebSocket",
                "destination", destination,
                "data", mockLocation
        ));
    }
}
