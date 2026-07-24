package org.sep490.backend.module.groupquest.entity.enumuration;

public enum GroupParticipantAction {
    PENDING, // wait for approval
    DENIED, // request to join denied
    JOIN,
    LEAVE,
    KICKED,
    DISMISSED // group was deleted
}

// 1. ai cũng refresh và copy đc link invite
// 2. api get GP pending
// 3. api approve/deny GP pending
// 4. api get all group for explorer