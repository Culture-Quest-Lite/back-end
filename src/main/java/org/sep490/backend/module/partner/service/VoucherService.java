package org.sep490.backend.module.partner.service;

import org.sep490.backend.module.partner.dto.filter.VoucherFilter;
import org.sep490.backend.module.partner.dto.request.VoucherRequest;
import org.sep490.backend.module.partner.dto.response.UserVoucherResponse;
import org.sep490.backend.module.partner.dto.response.VoucherResponse;
import org.springframework.data.domain.Page;

public interface VoucherService {
    VoucherResponse createVoucher(VoucherRequest request);
    VoucherResponse updateVoucher(Long id, VoucherRequest request);
    VoucherResponse getVoucherById(Long id);
    Page<VoucherResponse> getVouchers(VoucherFilter filter);
    void deleteVoucher(Long id);
    Page<VoucherResponse> getAvailableVouchers(VoucherFilter filter);
    UserVoucherResponse redeemVoucher(Long voucherId);
}
