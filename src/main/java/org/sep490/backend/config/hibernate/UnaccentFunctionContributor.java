package org.sep490.backend.config.hibernate;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Đăng ký hàm unaccent() của PostgreSQL để dùng được trong Criteria API.
 * Hibernate 6 không cho gọi hàm chưa đăng ký qua cb.function().
 *
 * <p>Yêu cầu extension đã được bật trên database:
 * {@code CREATE EXTENSION IF NOT EXISTS unaccent;}
 * (xem migration/2026-08-02-unaccent-extension.sql)
 */
public class UnaccentFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions.getFunctionRegistry().registerPattern(
                "unaccent",
                "unaccent(?1)",
                functionContributions.getTypeConfiguration()
                        .getBasicTypeRegistry()
                        .resolve(StandardBasicTypes.STRING));
    }
}
