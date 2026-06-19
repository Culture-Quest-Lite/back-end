package org.sep490.backend.module.partner.specification;

import jakarta.persistence.criteria.Predicate;
import org.sep490.backend.module.partner.dto.filter.VoucherFilter;
import org.sep490.backend.module.partner.entity.Voucher;
import org.sep490.backend.module.partner.entity.enumeration.VoucherStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class VoucherSpecification {
    public static Specification<Voucher> filterVouchers(VoucherFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String keyword = "%" + filter.getSearch().trim().toLowerCase() + "%";
                Predicate codePredicate = cb.like(cb.lower(root.get("voucherCode")), keyword);
                Predicate namePredicate = cb.like(cb.lower(root.get("voucherName")), keyword);
                predicates.add(cb.or(codePredicate, namePredicate));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            } else {
                predicates.add(cb.notEqual(root.get("status"), VoucherStatus.DELETED));
            }

            if (filter.getPartnerId() != null) {
                predicates.add(cb.equal(root.get("partner").get("userId"), filter.getPartnerId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
