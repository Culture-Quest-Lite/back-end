package org.sep490.backend.module.groupquest.dto.ws;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationMessage {
    private Long userId;

    @JsonAlias({"username", "userName", "senderName"})
    private String displayName;

    private Double latitude;
    private Double longitude;

    /**
     * Chỉ dùng để trả ra: LocationWebSocketController luôn ghi đè bằng
     * LocalDateTime.now() nên giá trị client gửi lên là vô nghĩa.
     *
     * READ_ONLY để Jackson bỏ qua hoàn toàn khi deserialize. Trước đây field
     * này nhận cả input: client gửi timestamp dạng epoch millis (số) thì Jackson
     * ném MismatchedInputException ("raw timestamp not allowed for
     * java.time.LocalDateTime") ngay ở bước convert payload — handler
     * @MessageMapping không bao giờ chạy, cả nhóm không ai nhận được vị trí và
     * STOMP session bị đóng kèm ERROR frame.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime timestamp;
}
