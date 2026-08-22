package org.sep490.backend.module.admin.service;

import org.sep490.backend.module.admin.dto.request.CheckInRadiusConfigRequest;
import org.sep490.backend.module.admin.dto.response.CheckInRadiusConfigResponse;
import org.sep490.backend.module.admin.entity.CheckInRadiusConfig;

public interface CheckInRadiusConfigService {

    /** Cấu hình đang áp dụng; tự khởi tạo dòng mặc định nếu chưa có. */
    CheckInRadiusConfig getCurrent();

    CheckInRadiusConfigResponse getConfig();

    CheckInRadiusConfigResponse updateConfig(CheckInRadiusConfigRequest request);

    /**
     * Chuẩn hoá bán kính curator gửi lên: null thì lấy mặc định của admin,
     * có giá trị thì phải nằm trong khoảng admin cho phép.
     */
    int resolveRadius(Integer requestedRadius);

    /** Bán kính áp dụng cho hotspot chưa có cấu hình riêng. */
    int getDefaultRadius();
}
