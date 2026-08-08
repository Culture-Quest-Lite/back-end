package org.sep490.backend.module.partner.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.module.partner.entity.enumeration.VoucherStatus;
import org.sep490.backend.module.partner.repository.VoucherRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoucherExpiryScheduler {

    private static final List<VoucherStatus> EXPIRABLE_STATUSES =
            List.of(VoucherStatus.ACTIVE, VoucherStatus.INACTIVE, VoucherStatus.PENDING);

    private final VoucherRepository voucherRepository;

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void expireVouchers() {
        LocalDateTime now = LocalDateTime.now();
        int expired = voucherRepository.expireVouchers(VoucherStatus.EXPIRED, EXPIRABLE_STATUSES, now);
        if (expired > 0) {
            log.info("[VoucherExpiry] Đã expire {} voucher hết hạn", expired);
        }
    }
}
