package org.sep490.backend.common.dto;

import lombok.Data;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;

@Data
public class BaseFilterRequest {
    private String search;
    private UserStatus status;
    private int page = 0;
    private int size = 10;
    private String sortBy = "userId";
    private String sortDir = "asc";
}
