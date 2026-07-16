package org.sep490.backend.module.gamification.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.partner.dto.filter.VoucherFilter;
import org.sep490.backend.module.partner.dto.response.VoucherUsageResponse;
import org.sep490.backend.module.partner.dto.response.VoucherResponse;
import org.sep490.backend.module.partner.service.VoucherService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vouchers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VoucherController {

    VoucherService voucherService;

    @GetMapping("/{id}")
    public ResponseEntity<VoucherResponse> getVoucherById(@PathVariable Long id) {
        return ResponseEntity.ok(voucherService.getVoucherById(id));
    }

    @GetMapping("/available")
    public ResponseEntity<Page<VoucherResponse>> getAvailableVouchers(
            @Valid @ParameterObject @ModelAttribute VoucherFilter filter) {
        return ResponseEntity.ok(voucherService.getAvailableVouchers(filter));
    }

    @PostMapping("/{id}/redeem")
    public ResponseEntity<VoucherUsageResponse> redeemVoucher(@PathVariable Long id) {
        return ResponseEntity.ok(voucherService.redeemVoucher(id));
    }
}

