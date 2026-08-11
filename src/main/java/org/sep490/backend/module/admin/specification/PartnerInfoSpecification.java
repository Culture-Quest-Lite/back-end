package org.sep490.backend.module.admin.specification;

import jakarta.persistence.criteria.*;
import org.locationtech.jts.geom.Point;
import org.sep490.backend.module.admin.entity.PartnerInfo;
import org.sep490.backend.module.content.entity.Hotspot;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class PartnerInfoSpecification {

    public static Specification<PartnerInfo> isNearAnyLocation(List<Point> locations, Double distanceMeters) {
        return (Root<PartnerInfo> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {

            // 1. Kiểm tra đầu vào: Nếu list rỗng hoặc distance null thì trả về disjunction (không tìm thấy gì)
            if (locations == null || locations.isEmpty() || distanceMeters == null) {
                return cb.disjunction();
            }

            List<Predicate> orPredicates = new ArrayList<>();

            // 2. Lặp qua từng Point truyền vào để tạo điều kiện khoảng cách
            for (Point point : locations) {

                // ST_DistanceSphere(partner.location, point_truyen_vao) <= distanceMeters
                Expression<Double> distanceExpr = cb.function(
                        "ST_DistanceSphere",
                        Double.class,
                        root.get("location"),        // Point của PartnerInfo dưới DB
                        cb.literal(point)            // Point truyền vào từ API
                );

                Predicate withinDistance = cb.lessThanOrEqualTo(distanceExpr, distanceMeters);
                orPredicates.add(withinDistance);
            }

            // 3. Trả về: (Gần điểm 1) OR (Gần điểm 2) OR ... OR (Gần điểm N)
            return cb.or(orPredicates.toArray(new Predicate[0]));
        };
    }
}
