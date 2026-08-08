package org.sep490.backend.module.admin.dto.projection;

public interface DailyCountProjection {
    Integer getBucketYear();
    Integer getBucketMonth();
    Integer getBucketDay();
    Long getTotal();
}
