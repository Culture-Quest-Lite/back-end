package org.sep490.backend.module.admin.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.admin.dto.response.AuditLogResponse;
import org.sep490.backend.module.authentication.entity.AuditLog;
import org.sep490.backend.module.authentication.entity.User;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuditLogMapper {

    @Mapping(target = "actor", source = "user")
    AuditLogResponse toResponse(AuditLog entity);

    AuditLogResponse.ActorResponse toActorResponse(User user);

    ObjectMapper AUDIT_VALUE_MAPPER = new ObjectMapper();

    //oldValue/newValue lưu dưới dạng chuỗi JSON, trả về dạng object cho client dễ đọc
    default JsonNode toJsonNode(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return AUDIT_VALUE_MAPPER.readTree(rawValue);
        } catch (Exception e) {
            //Giá trị không phải JSON hợp lệ (hoặc đã bị cắt bớt) thì giữ nguyên dạng chuỗi
            return TextNode.valueOf(rawValue);
        }
    }
}
