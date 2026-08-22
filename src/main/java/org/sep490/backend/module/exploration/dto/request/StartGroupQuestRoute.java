package org.sep490.backend.module.exploration.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StartGroupQuestRoute {
    Long routeId;
    Long groupId;
}
