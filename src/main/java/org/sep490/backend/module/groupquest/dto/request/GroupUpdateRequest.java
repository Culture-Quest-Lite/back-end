package org.sep490.backend.module.groupquest.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroupUpdateRequest {
    String groupName;
    Boolean requiredApproval;
}
