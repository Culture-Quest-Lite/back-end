package org.sep490.backend.module.groupquest.entity.enumuration;

public enum GroupParticipantAction {
    PENDING, // wait for approval
    DENIED, // request to join denied
    JOIN,
    LEAVE,
    KICKED,
    DISMISSED // group was deleted
}