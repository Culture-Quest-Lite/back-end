package org.sep490.backend.common.filter.specification;

import jakarta.persistence.criteria.*;
import org.sep490.backend.common.filter.dto.FilterRequest;
import org.sep490.backend.common.filter.dto.SearchRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class GenericSpecification<T> implements Specification<T> {

    private final SearchRequest request;

    public GenericSpecification(SearchRequest request) {
        this.request = request;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        query.distinct(true);

        for (FilterRequest filter : request.getFilters()) {
            if (filter.getValue() == null && (filter.getValues() == null || filter.getValues().isEmpty())) {
                continue;
            }

            Path<?> expression;

            if (filter.getField().contains(".")) {
                String[] parts = filter.getField().split("\\.");
                Join<Object, Object> join = root.join(parts[0], JoinType.INNER);
                expression = join.get(parts[1]);
            } else {
                expression = root.get(filter.getField());
            }

            Object value = filter.getValue();

            if (expression.getJavaType().isEnum() && value != null) {
                value = Enum.valueOf((Class<Enum>) expression.getJavaType(), value.toString());
            }

            switch (filter.getOperator()) {
                case EQUALS:
                    predicates.add(cb.equal(expression, value));
                    break;
                case NOT_EQUALS:
                    predicates.add(cb.notEqual(expression, value));
                    break;
                case LIKE:
                    predicates.add(cb.like(cb.lower((Expression<String>) expression), "%" + value.toString().toLowerCase() + "%"));
                    break;
                case GREATER_THAN:
                    predicates.add(cb.greaterThan((Expression<Comparable>) expression, (Comparable) value));
                    break;
                case LESS_THAN:
                    predicates.add(cb.lessThan((Expression<Comparable>) expression, (Comparable) value));
                    break;
                case GREATER_THAN_OR_EQUAL:
                    predicates.add(cb.greaterThanOrEqualTo((Expression<Comparable>) expression, (Comparable) value));
                    break;
                case LESS_THAN_OR_EQUAL:
                    predicates.add(cb.lessThanOrEqualTo((Expression<Comparable>) expression, (Comparable) value));
                    break;
                case IN:
                    CriteriaBuilder.In<Object> inClause = cb.in(expression);
                    for (Object val : filter.getValues()) {
                        if (expression.getJavaType().isEnum()) {
                            inClause.value(Enum.valueOf((Class<Enum>) expression.getJavaType(), val.toString()));
                        } else {
                            inClause.value(val);
                        }
                    }
                    predicates.add(inClause);
                    break;
            }
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }
}
