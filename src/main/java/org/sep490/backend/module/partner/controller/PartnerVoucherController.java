package org.sep490.backend.module.partner.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.partner.dto.filter.VoucherFilter;
import org.sep490.backend.module.partner.dto.request.VoucherRequest;
import org.sep490.backend.module.partner.dto.response.UserVoucherResponse;
import org.sep490.backend.module.partner.dto.response.VoucherResponse;
import org.sep490.backend.module.partner.service.VoucherService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/partner/vouchers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PartnerVoucherController {

    VoucherService voucherService;

    @GetMapping
    public ResponseEntity<Page<VoucherResponse>> getVouchers(
            @Valid @ParameterObject @ModelAttribute VoucherFilter filter) {
        return ResponseEntity.ok(voucherService.getVouchers(filter));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VoucherResponse> createVoucher(@Valid @ModelAttribute VoucherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voucherService.createVoucher(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VoucherResponse> updateVoucher(
            @PathVariable Long id,
            @Valid @RequestBody VoucherRequest request) {
        return ResponseEntity.ok(voucherService.updateVoucher(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVoucher(@PathVariable Long id) {
        voucherService.deleteVoucher(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/use")
    public ResponseEntity<UserVoucherResponse> useVoucher(@RequestParam String voucherCode) {
        return ResponseEntity.ok(voucherService.useVoucher(voucherCode));
    }
}
