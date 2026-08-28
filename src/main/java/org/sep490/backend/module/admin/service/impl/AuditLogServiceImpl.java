package org.sep490.backend.module.admin.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.utils.SecurityUtils;
import org.sep490.backend.module.admin.dto.filter.AuditLogFilterRequest;
import org.sep490.backend.module.admin.dto.response.AuditLogResponse;
import org.sep490.backend.module.admin.entity.enumeration.AuditAction;
import org.sep490.backend.module.admin.mapper.AuditLogMapper;
import org.sep490.backend.module.admin.repository.AuditLogRepository;
import org.sep490.backend.module.admin.service.AuditLogService;
import org.sep490.backend.module.admin.specification.AuditLogSpecification;
import org.sep490.backend.module.authentication.entity.AuditLog;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditLogServiceImpl implements AuditLogService {

    static int MAX_VALUE_LENGTH = 4000;

    static int MAX_PAGE_SIZE = 100;

    /** Chỉ cho sắp xếp theo cột có thật, tránh dò tên thuộc tính qua tham số sortBy. */
    static Set<String> SORTABLE_FIELDS = Set.of(
            "createdAt", "action", "tableName", "recordId", "endpoint", "ipAddress");

    AuditLogRepository auditLogRepository;
    UserRepository userRepository;
    AuditLogMapper auditLogMapper;
    ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, String tableName, String recordId, Object oldValue, Object newValue) {
        save(action, tableName, recordId, oldValue, newValue, currentEndpoint());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logWithEndpoint(AuditAction action, String tableName, String recordId,
                                Object oldValue, Object newValue, String endpoint) {
        save(action, tableName, recordId, oldValue, newValue, endpoint);
    }

    private void save(AuditAction action, String tableName, String recordId,
                      Object oldValue, Object newValue, String endpoint) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(resolveActor())
                    .action(action != null ? action : AuditAction.UNKNOWN)
                    .tableName(tableName)
                    .recordId(recordId)
                    .endpoint(endpoint)
                    .oldValue(serialize(oldValue))
                    .newValue(serialize(newValue))
                    .ipAddress(resolveIpAddress())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Không ghi được audit log cho action {} trên {} {}: {}",
                    action, tableName, recordId, e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getLogs(AuditLogFilterRequest filter) {
        if (filter == null) {
            throw new BusinessException("Bộ lọc nhật ký không được để trống");
        }
        if (filter.getPage() < 0) {
            throw new BusinessException("Số trang không được nhỏ hơn 0");
        }
        if (filter.getSize() <= 0) {
            throw new BusinessException("Kích thước trang phải lớn hơn 0");
        }
        if (filter.getSize() > MAX_PAGE_SIZE) {
            throw new BusinessException("Kích thước trang không được vượt quá 100");
        }
        if (!SORTABLE_FIELDS.contains(filter.getSortBy())) {
            throw new BusinessException("Không hỗ trợ sắp xếp theo trường: " + filter.getSortBy());
        }
        if (filter.getFrom() != null && filter.getTo() != null
                && filter.getFrom().isAfter(filter.getTo())) {
            throw new BusinessException("Thời gian bắt đầu phải trước thời gian kết thúc");
        }
        Sort sort = filter.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(filter.getSortBy()).ascending()
                : Sort.by(filter.getSortBy()).descending();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);
        Specification<AuditLog> spec = AuditLogSpecification.filter(
                filter.getSearch(), filter.getUserId(), filter.getAction(),
                filter.getTableName(), filter.getFrom(), filter.getTo());
        return auditLogRepository.findAll(spec, pageable).map(auditLogMapper::toResponse);
    }

    private User resolveActor() {
        return SecurityUtils.getCurrentUserKeyCloakId()
                .flatMap(userRepository::findByKeycloakUserId)
                .orElse(null);
    }

    private String resolveIpAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String currentEndpoint() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getMethod() + " " + request.getRequestURI();
    }

    private HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        String json;
        try {
            json = value instanceof String str ? str : objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            json = String.valueOf(value);
        }
        return json.length() > MAX_VALUE_LENGTH ? json.substring(0, MAX_VALUE_LENGTH) : json;
    }
}
