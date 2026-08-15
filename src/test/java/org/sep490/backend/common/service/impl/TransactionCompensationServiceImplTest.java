package org.sep490.backend.common.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho CƠ CHẾ BÙ TRỪ GIAO DỊCH (dọn rác S3/PayOS khi transaction rollback).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
class TransactionCompensationServiceImplTest {

    private final TransactionCompensationServiceImpl service = new TransactionCompensationServiceImpl();

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static void openTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private static List<TransactionSynchronization> registered() {
        return TransactionSynchronizationManager.getSynchronizations();
    }

    // =====================================================================
    // Function: runOnRollback
    // =====================================================================
    @Nested
    @DisplayName("runOnRollback")
    class RunOnRollbackTest {

        // UTCID01 - Abnormal: không có transaction -> bỏ qua, KHÔNG chạy tác vụ dọn dẹp
        @Test
        void runOnRollback_noActiveTransaction_doesNotRunAction() {
            AtomicInteger counter = new AtomicInteger();

            service.runOnRollback("Xóa S3 object media/abc.jpg", counter::incrementAndGet);

            assertEquals(0, counter.get());
        }

        // UTCID02 - Normal: transaction rollback -> chạy tác vụ dọn dẹp đúng 1 lần
        @Test
        void runOnRollback_transactionRolledBack_runsAction() {
            openTransaction();
            AtomicInteger counter = new AtomicInteger();

            service.runOnRollback("Xóa S3 object media/abc.jpg", counter::incrementAndGet);
            registered().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            assertEquals(1, counter.get());
        }

        // UTCID03 - Normal: transaction commit thành công -> KHÔNG dọn dẹp (file phải giữ lại)
        @Test
        void runOnRollback_transactionCommitted_doesNotRunAction() {
            openTransaction();
            AtomicInteger counter = new AtomicInteger();

            service.runOnRollback("Xóa S3 object media/abc.jpg", counter::incrementAndGet);
            registered().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

            assertEquals(0, counter.get());
        }

        // UTCID04 - Abnormal: tác vụ dọn dẹp ném lỗi (S3 timeout) -> nuốt lỗi, không phá luồng rollback
        @Test
        void runOnRollback_actionThrows_exceptionIsSwallowed() {
            openTransaction();

            service.runOnRollback("Xóa S3 object media/abc.jpg", () -> {
                throw new IllegalStateException("S3 timeout");
            });

            assertDoesNotThrow(() -> registered()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)));
        }
    }

    // =====================================================================
    // Function: runAfterCommit
    // =====================================================================
    @Nested
    @DisplayName("runAfterCommit")
    class RunAfterCommitTest {

        // UTCID01 - Abnormal: không có transaction -> chạy ngay lập tức
        @Test
        void runAfterCommit_noActiveTransaction_runsImmediately() {
            AtomicInteger counter = new AtomicInteger();

            service.runAfterCommit("Gửi push notification", counter::incrementAndGet);

            assertEquals(1, counter.get());
        }

        // UTCID02 - Normal: có transaction -> hoãn lại, chỉ chạy sau khi commit
        @Test
        void runAfterCommit_activeTransaction_deferredUntilCommit() {
            openTransaction();
            AtomicInteger counter = new AtomicInteger();

            service.runAfterCommit("Gửi push notification", counter::incrementAndGet);
            assertEquals(0, counter.get(), "Chưa commit thì chưa được chạy");

            registered().forEach(TransactionSynchronization::afterCommit);
            assertEquals(1, counter.get());
        }

        // UTCID03 - Abnormal: tác vụ ném lỗi sau commit -> nuốt lỗi, không rollback dữ liệu đã ghi
        @Test
        void runAfterCommit_actionThrows_exceptionIsSwallowed() {
            openTransaction();

            service.runAfterCommit("Gửi push notification", () -> {
                throw new IllegalStateException("FCM lỗi");
            });

            assertDoesNotThrow(() -> registered().forEach(TransactionSynchronization::afterCommit));
        }

        // UTCID04 - Normal: đăng ký nhiều tác vụ -> tất cả đều được chạy sau commit
        @Test
        void runAfterCommit_multipleActions_allAreExecuted() {
            openTransaction();
            AtomicInteger counter = new AtomicInteger();

            service.runAfterCommit("Gửi push notification", counter::incrementAndGet);
            service.runAfterCommit("Ghi audit log", counter::incrementAndGet);
            registered().forEach(TransactionSynchronization::afterCommit);

            assertEquals(2, counter.get());
        }
    }
}
