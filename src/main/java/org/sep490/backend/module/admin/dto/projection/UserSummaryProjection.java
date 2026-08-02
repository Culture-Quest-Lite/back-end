package org.sep490.backend.module.admin.dto.projection;

public interface UserSummaryProjection {
    Long getTotalUsers();
    Long getActiveUsers();
    Long getNewUsersThisMonth();
}
