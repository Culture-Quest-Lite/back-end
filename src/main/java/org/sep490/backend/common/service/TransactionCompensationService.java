package org.sep490.backend.common.service;

public interface TransactionCompensationService {
    void runOnRollback(String description, Runnable action);

    void runAfterCommit(String description, Runnable action);
}
