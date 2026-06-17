package org.sep490.backend.module.partner.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sep490.backend.module.partner.dto.response.UserVoucherResponse;
import org.sep490.backend.module.partner.entity.UserVoucher;

@Mapper(componentModel = "spring")
public interface UserVoucherMapper {

    @Mapping(target = "voucherId", source = "voucher.voucherId")
    @Mapping(target = "voucherCode", source = "voucher.voucherCode")
    @Mapping(target = "voucherName", source = "voucher.voucherName")
    @Mapping(target = "description", source = "voucher.description")
    @Mapping(target = "pointsRequired", source = "voucher.pointsRequired")
    UserVoucherResponse toResponse(UserVoucher userVoucher);
}
