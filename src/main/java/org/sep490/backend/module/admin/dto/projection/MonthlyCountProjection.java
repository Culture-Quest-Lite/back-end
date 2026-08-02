package org.sep490.backend.module.admin.dto.projection;

public interface MonthlyCountProjection {
    Integer getBucketYear();
    Integer getBucketMonth();
    Long getTotal();
}
