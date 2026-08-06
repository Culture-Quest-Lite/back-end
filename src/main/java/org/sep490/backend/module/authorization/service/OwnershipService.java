package org.sep490.backend.module.authorization.service;

public interface OwnershipService {
    boolean isOwner(Long resourceId, String type);
    boolean isOwnerOrHasPerm(Long resourceId, String type, String permissionCode);
    boolean isGroupLeader(Long groupId);
}
