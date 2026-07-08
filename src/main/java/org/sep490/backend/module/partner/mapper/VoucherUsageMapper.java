package org.sep490.backend.module.partner.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sep490.backend.module.partner.dto.response.VoucherUsageResponse;
import org.sep490.backend.module.partner.entity.VoucherUsage;

@Mapper(componentModel = "spring")
public interface VoucherUsageMapper {

    @Mapping(target = "voucherId", source = "voucherUsage.voucher.voucherId")
    @Mapping(target = "voucherCode", source = "voucherUsage.voucher.voucherCode")
    @Mapping(target = "voucherName", source = "voucherUsage.voucher.voucherName")
    @Mapping(target = "description", source = "voucherUsage.voucher.description")
    @Mapping(target = "pointsRequired", source = "voucherUsage.voucher.pointsRequired")
    VoucherUsageResponse toResponse(VoucherUsage voucherUsage);
}
