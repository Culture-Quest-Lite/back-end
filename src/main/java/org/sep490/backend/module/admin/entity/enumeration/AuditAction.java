package org.sep490.backend.module.admin.entity.enumeration;

public enum AuditAction {
    LOCK_USER,
    UNLOCK_USER,
    UPDATE_USER_ROLE,
    APPROVE_POST,
    REJECT_POST,
    BAN_POST,
    VERIFY_SUBSCRIPTION,
    CREATE_SUBSCRIPTION_PLAN,
    UPDATE_SUBSCRIPTION_PLAN,
    DELETE_SUBSCRIPTION_PLAN,
    //Endpoint admin chưa được khai báo @Auditable vẫn được ghi lại kèm endpoint đầy đủ
    UNKNOWN
}
