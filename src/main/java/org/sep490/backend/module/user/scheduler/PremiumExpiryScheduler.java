package org.sep490.backend.module.user.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.sep490.backend.module.admin.repository.InvoiceRepository;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PremiumExpiryScheduler {

    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void expirePremiumInvoices() {
        LocalDateTime now = LocalDateTime.now();
        List<Invoice> expired = invoiceRepository
                .findByUserIsNotNullAndStatusAndEndDateBefore(InvoiceStatus.ACTIVE, now);
        for (Invoice inv : expired) {
            inv.setStatus(InvoiceStatus.EXPIRED);
            User u = inv.getUser();
            boolean stillActive = invoiceRepository
                    .existsByUser_UserIdAndStatusAndEndDateAfter(u.getUserId(), InvoiceStatus.ACTIVE, now);
            if (!stillActive) {
                u.setIsPremium(false);
                userRepository.save(u);
            }
        }
        invoiceRepository.saveAll(expired);
        if (!expired.isEmpty()) {
            log.info("[PremiumExpiry] Đã expire {} invoice premium", expired.size());
        }
    }
}
