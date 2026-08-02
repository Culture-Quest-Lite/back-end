package org.sep490.backend.common.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.service.TransactionCompensationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
public class TransactionCompensationServiceImpl implements TransactionCompensationService {

    @Override
    public void runOnRollback(String description, Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("Không có transaction active, bỏ qua đăng ký rollback: {}", description);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    log.warn("Transaction rollback, tiến hành dọn dẹp: {}", description);
                    safeRun(description, action);
                }
            }
        });
    }

    @Override
    public void runAfterCommit(String description, Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeRun(description, action);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeRun(description, action);
            }
        });
    }

    private void safeRun(String description, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("Lỗi khi thực thi tác vụ [{}] — có thể cần xử lý thủ công", description, e);
        }
    }
}
