package org.sep490.backend.module.user.dto.request;

import lombok.Data;
import org.sep490.backend.module.user.entity.enumeration.UserRole;

import java.util.List;

@Data
public class UpdateUserRoleRequest {
    private UserRole roles;
}
