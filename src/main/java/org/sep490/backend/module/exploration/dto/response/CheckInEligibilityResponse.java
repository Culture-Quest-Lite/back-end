package org.sep490.backend.module.exploration.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Cho phép client biết TRƯỚC là có check-in được hay không, để bật/tắt nút
 * check-in theo thời gian thực thay vì bấm rồi mới nhận lỗi.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckInEligibilityResponse {
    Boolean eligible;
    Double distanceMeters;
    Double requiredMeters;
    Boolean alreadyCheckedIn;
    String message;
}
