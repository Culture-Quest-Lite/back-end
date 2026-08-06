package org.sep490.backend.module.user.service;

/**
 * Cache ánh xạ keycloakUserId -> userId.
 *
 * getCurrentUser() được gọi ở gần như MỌI request đã xác thực, mỗi lần một query
 * findByKeycloakUserId. Cache ánh xạ này giúp chuyển sang findById (tra theo khoá chính).
 *
 * CHỈ cache id, KHÔNG cache entity User: entity có lazy association và bị persistence
 * context quản lý — cache nó sẽ gây LazyInitializationException hoặc trả dữ liệu cũ.
 */
public interface UserIdCacheService {

    /** Trả null nếu không tìm thấy. */
    Long resolveUserId(String keycloakUserId);

    /** Gọi khi user bị khoá/mở khoá/xoá để lần sau đọc lại từ DB. */
    void evict(String keycloakUserId);
}
