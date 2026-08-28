package org.sep490.backend.module.admin.annotation;

import org.sep490.backend.module.admin.entity.enumeration.AuditAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {

    AuditAction value();

    String tableName() default "";

    //Bật cho endpoint đã tự ghi log chi tiết (có oldValue) trong service, tránh log trùng
    boolean skipAspect() default false;
}
