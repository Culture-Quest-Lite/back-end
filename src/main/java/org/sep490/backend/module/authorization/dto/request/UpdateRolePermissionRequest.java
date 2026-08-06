package org.sep490.backend.module.authorization.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "Tập quyền ĐẦY ĐỦ của một vai trò sau khi sửa")
@Data
public class UpdateRolePermissionRequest {

    @Schema(description = """
            Toàn bộ mã quyền mà role sẽ có, KHÔNG kèm tiền tố PERM_.
            Là ghi đè: mã nào không có trong danh sách sẽ bị gỡ khỏi role.
            Gửi mảng rỗng = gỡ sạch quyền của role đó.
            """,
            example = "[\"HOTSPOT_MANAGE\", \"ROUTE_MANAGE\", \"STORY_MANAGE\", \"TAG_MANAGE\", \"DASHBOARD_CURATOR_VIEW\"]")
    @NotNull(message = "Danh sách quyền không được để trống")
    private List<String> codes;
}
