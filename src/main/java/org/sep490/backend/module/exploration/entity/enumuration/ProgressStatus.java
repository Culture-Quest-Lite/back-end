package org.sep490.backend.module.exploration.entity.enumuration;

public enum ProgressStatus {
    IN_PROGRESS,
    COMPLETED,
    ABANDONED, // bỏ luôn
    ON_HOLD // không đi nữa nhưng quên kh abandon, có thể start để tiếp tục đi
}
