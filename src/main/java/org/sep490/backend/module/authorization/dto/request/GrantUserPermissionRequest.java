package org.sep490.backend.module.authorization.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "Ngoại lệ quyền ở cấp cá nhân, đè lên quyền của role")
@Data
public class GrantUserPermissionRequest {

    @Schema(description = "Mã quyền, KHÔNG kèm tiền tố PERM_", example = "REVIEW_MODERATE")
    @NotBlank(message = "Mã quyền không được để trống")
    private String code;

    @Schema(description = "true = cấp thêm quyền role không có; false = thu hồi quyền role đang có",
            example = "true")
    @NotNull(message = "Phải chỉ rõ cấp hay thu hồi")
    private Boolean granted;

    @Schema(description = "Thời điểm hết hiệu lực. Bỏ trống = vĩnh viễn",
            example = "2026-09-01T00:00:00")
    private LocalDateTime expiresAt;

    @Schema(description = "Lý do cấp/thu hồi, bắt buộc để sau này đọc audit log còn hiểu",
            example = "Hỗ trợ đợt kiểm duyệt tháng 8")
    @NotBlank(message = "Phải nêu lý do để audit về sau còn hiểu")
    private String reason;
}
