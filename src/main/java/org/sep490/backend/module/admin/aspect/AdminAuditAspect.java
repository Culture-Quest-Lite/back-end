package org.sep490.backend.module.admin.aspect;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.sep490.backend.module.admin.annotation.Auditable;
import org.sep490.backend.module.admin.entity.enumeration.AuditAction;
import org.sep490.backend.module.admin.service.AuditLogService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;

@Aspect
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminAuditAspect {

    static Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    AuditLogService auditLogService;

    @Pointcut("within(org.sep490.backend.module.admin.controller..*)")
    public void adminController() {
    }

    //@AfterReturning chứ không phải @Around: chỉ ghi log khi hành động đã thành công.
    @AfterReturning("adminController()")
    public void auditAdminAction(JoinPoint joinPoint) {
        HttpServletRequest request = currentRequest();
        if (request == null || !WRITE_METHODS.contains(request.getMethod())) {
            return;
        }

        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Auditable auditable = method.getAnnotation(Auditable.class);
        if (auditable != null && auditable.skipAspect()) {
            return;
        }

        AuditAction action = auditable != null ? auditable.value() : AuditAction.UNKNOWN;
        String tableName = auditable != null && !auditable.tableName().isBlank() ? auditable.tableName() : null;
        String endpoint = request.getMethod() + " " + request.getRequestURI();

        auditLogService.logWithEndpoint(action, tableName, extractRecordId(method, joinPoint.getArgs()),
                null, extractRequestBody(method, joinPoint.getArgs()), endpoint);
    }

    private String extractRecordId(Method method, Object[] args) {
        Object id = findFirstArgAnnotatedWith(method, args, PathVariable.class);
        return id == null ? null : String.valueOf(id);
    }

    private Object extractRequestBody(Method method, Object[] args) {
        Object body = findFirstArgAnnotatedWith(method, args, RequestBody.class);
        if (body instanceof MultipartFile || body instanceof HttpServletRequest || body instanceof HttpServletResponse) {
            return null;
        }
        return body;
    }

    private Object findFirstArgAnnotatedWith(Method method, Object[] args, Class<? extends Annotation> annotation) {
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        for (int i = 0; i < parameterAnnotations.length && i < args.length; i++) {
            for (Annotation parameterAnnotation : parameterAnnotations[i]) {
                if (annotation.isInstance(parameterAnnotation) && args[i] != null) {
                    return args[i];
                }
            }
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }
}
