package org.sep490.backend.module.partner.mapper;

import org.mapstruct.*;
import org.sep490.backend.module.partner.dto.request.VoucherRequest;
import org.sep490.backend.module.partner.dto.response.VoucherResponse;
import org.sep490.backend.module.partner.entity.Voucher;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VoucherMapper {

    @Mapping(target = "quantityRemaining", source = "quantityTotal")
    @Mapping(target = "voucherCode", ignore = true)
    @Mapping(target = "voucherId", ignore = true)
    @Mapping(target = "partner", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Voucher toEntity(VoucherRequest request);

    @Mapping(target = "partnerId", source = "partner.userId")
    @Mapping(target = "partnerName", source = "partner.displayName")
    VoucherResponse toResponse(Voucher entity);

    @Mapping(target = "voucherId", ignore = true)
    @Mapping(target = "voucherCode", ignore = true)
    @Mapping(target = "partner", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "quantityRemaining", ignore = true)
    void updateEntityFromRequest(VoucherRequest request, @MappingTarget Voucher entity);

    @BeforeMapping
    default void handleQuantityRemaining(VoucherRequest request, @MappingTarget Voucher entity) {
        if (request != null && entity != null) {
            long diffQuantity = request.getQuantityTotal() - entity.getQuantityTotal();
            entity.setQuantityRemaining(entity.getQuantityRemaining() + diffQuantity);
        }
    }
}
