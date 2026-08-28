package org.sep490.backend.module.admin.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.common.utils.SecurityUtils;
import org.sep490.backend.module.admin.dto.filter.AuditLogFilterRequest;
import org.sep490.backend.module.admin.dto.response.AuditLogResponse;
import org.sep490.backend.module.admin.entity.enumeration.AuditAction;
import org.sep490.backend.module.admin.mapper.AuditLogMapper;
import org.sep490.backend.module.admin.repository.AuditLogRepository;
import org.sep490.backend.module.authentication.entity.AuditLog;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.sep490.backend.common.exception.BusinessException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho NHẬT KÝ KIỂM TOÁN (ghi lại thao tác của admin: ai, làm gì, từ IP nào).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogServiceImplTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogMapper auditLogMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuditLogServiceImpl auditLogService;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogServiceImpl(
                auditLogRepository, userRepository, auditLogMapper, objectMapper);
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserKeyCloakId).thenReturn(Optional.of("kc-admin"));
        when(userRepository.findByKeycloakUserId("kc-admin")).thenReturn(Optional.of(admin()));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
        RequestContextHolder.resetRequestAttributes();
    }

    private static User admin() {
        User user = new User();
        user.setUserId(1L);
        user.setUsername("admin01");
        return user;
    }

    private static void bindRequest(String method, String uri, String forwardedFor, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private AuditLog captureSaved() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    // =====================================================================
    // Function: log
    // =====================================================================
    @Nested
    @DisplayName("log")
    class LogTest {

        // UTCID01 - Normal: ghi log kèm người thực hiện lấy từ token đang đăng nhập
        @Test
        void log_authenticatedAdmin_recordsActor() {
            bindRequest("POST", "/api/admin/users/5/lock", null, "192.168.1.10");

            auditLogService.log(AuditAction.LOCK_USER, "users", "5",
                    Map.of("status", "ACTIVE"), Map.of("status", "LOCKED"));

            AuditLog saved = captureSaved();
            assertEquals(1L, saved.getUser().getUserId());
            assertEquals(AuditAction.LOCK_USER, saved.getAction());
            assertEquals("users", saved.getTableName());
            assertEquals("5", saved.getRecordId());
        }

        // UTCID02 - Boundary: không xác định được người dùng -> vẫn ghi log với actor null
        @Test
        void log_noAuthenticatedUser_savesWithNullActor() {
            securityUtils.when(SecurityUtils::getCurrentUserKeyCloakId).thenReturn(Optional.empty());
            bindRequest("POST", "/api/admin/users/5/lock", null, "192.168.1.10");

            auditLogService.log(AuditAction.LOCK_USER, "users", "5", null, null);

            assertNull(captureSaved().getUser());
        }

        // UTCID03 - Boundary: action null -> quy về UNKNOWN thay vì ghi null
        @Test
        void log_nullAction_fallsBackToUnknown() {
            bindRequest("POST", "/api/admin/users/5/lock", null, "192.168.1.10");

            auditLogService.log(null, "users", "5", null, null);

            assertEquals(AuditAction.UNKNOWN, captureSaved().getAction());
        }

        // UTCID04 - Normal: ghi lại endpoint đang gọi theo dạng "METHOD URI"
        @Test
        void log_withHttpRequest_recordsEndpoint() {
            bindRequest("POST", "/api/admin/users/5/lock", null, "192.168.1.10");

            auditLogService.log(AuditAction.LOCK_USER, "users", "5", null, null);

            assertEquals("POST /api/admin/users/5/lock", captureSaved().getEndpoint());
        }

        // UTCID05 - Normal: lấy IP thật từ header X-Forwarded-For khi đứng sau proxy
        @Test
        void log_behindProxy_takesFirstForwardedIp() {
            bindRequest("POST", "/api/admin/users/5/lock",
                    "203.113.10.5, 10.0.0.1", "10.0.0.1");

            auditLogService.log(AuditAction.LOCK_USER, "users", "5", null, null);

            assertEquals("203.113.10.5", captureSaved().getIpAddress());
        }

        // UTCID06 - Boundary: không có proxy -> lấy IP trực tiếp của client
        @Test
        void log_withoutProxyHeader_takesRemoteAddr() {
            bindRequest("POST", "/api/admin/users/5/lock", null, "192.168.1.10");

            auditLogService.log(AuditAction.LOCK_USER, "users", "5", null, null);

            assertEquals("192.168.1.10", captureSaved().getIpAddress());
        }

        // UTCID07 - Boundary: chạy ngoài request HTTP (job nền) -> endpoint và IP là null
        @Test
        void log_outsideHttpRequest_endpointAndIpAreNull() {
            RequestContextHolder.resetRequestAttributes();

            auditLogService.log(AuditAction.UPDATE_USER_ROLE, "users", "5", null, null);

            AuditLog saved = captureSaved();
            assertNull(saved.getEndpoint());
            assertNull(saved.getIpAddress());
        }

        // UTCID08 - Normal: giá trị cũ/mới dạng object -> được tuần tự hóa thành JSON
        @Test
        void log_objectValues_areSerializedToJson() {
            bindRequest("POST", "/api/admin/users/5/lock", null, "192.168.1.10");

            auditLogService.log(AuditAction.LOCK_USER, "users", "5",
                    Map.of("status", "ACTIVE"), Map.of("status", "LOCKED"));

            AuditLog saved = captureSaved();
            assertEquals("{\"status\":\"ACTIVE\"}", saved.getOldValue());
            assertEquals("{\"status\":\"LOCKED\"}", saved.getNewValue());
        }

        // UTCID09 - Boundary: giá trị là String -> giữ nguyên, không bọc thêm dấu nháy JSON
        @Test
        void log_stringValues_areKeptAsIs() {
            bindRequest("POST", "/api/admin/users/5/lock", null, "192.168.1.10");

            auditLogService.log(AuditAction.LOCK_USER, "users", "5", "ACTIVE", "LOCKED");

            AuditLog saved = captureSaved();
            assertEquals("ACTIVE", saved.getOldValue());
            assertEquals("LOCKED", saved.getNewValue());
        }

        // UTCID10 - Boundary: giá trị null -> lưu null, không lưu chuỗi "null"
        @Test
        void log_nullValues_areStoredAsNull() {
            bindRequest("POST", "/api/admin/users/5/lock", null, "192.168.1.10");

            auditLogService.log(AuditAction.LOCK_USER, "users", "5", null, null);

            AuditLog saved = captureSaved();
            assertNull(saved.getOldValue());
            assertNull(saved.getNewValue());
        }

        // UTCID11 - Boundary: giá trị quá dài -> cắt còn tối đa 4000 ký tự để không vỡ cột DB
        @Test
        void log_veryLongValue_isTruncatedTo4000Chars() {
            bindRequest("POST", "/api/admin/content/1", null, "192.168.1.10");

            auditLogService.log(AuditAction.BAN_POST, "posts", "1", null, "x".repeat(5000));

            assertEquals(4000, captureSaved().getNewValue().length());
        }

        // UTCID12 - Abnormal: repository lỗi -> nuốt lỗi, KHÔNG làm hỏng nghiệp vụ chính
        @Test
        void log_repositoryThrows_exceptionIsSwallowed() {
            bindRequest("POST", "/api/admin/users/5/lock", null, "192.168.1.10");
            when(auditLogRepository.save(any(AuditLog.class)))
                    .thenThrow(new RuntimeException("DB connection lost"));

            assertDoesNotThrow(() ->
                    auditLogService.log(AuditAction.LOCK_USER, "users", "5", null, null));
        }
    }

    // =====================================================================
    // Function: logWithEndpoint
    // =====================================================================
    @Nested
    @DisplayName("logWithEndpoint")
    class LogWithEndpointTest {

        // UTCID01 - Normal: endpoint được truyền tay -> ghi đúng giá trị đó
        @Test
        void logWithEndpoint_explicitEndpoint_isRecorded() {
            auditLogService.logWithEndpoint(AuditAction.VERIFY_SUBSCRIPTION, "invoices", "12",
                    null, null, "PUT /api/admin/subscriptions/12/verify");

            assertEquals("PUT /api/admin/subscriptions/12/verify", captureSaved().getEndpoint());
        }

        // UTCID02 - Boundary: endpoint null -> ghi null, không suy ra từ request hiện tại
        @Test
        void logWithEndpoint_nullEndpoint_isRecordedAsNull() {
            bindRequest("POST", "/api/khac", null, "192.168.1.10");

            auditLogService.logWithEndpoint(AuditAction.VERIFY_SUBSCRIPTION, "invoices", "12",
                    null, null, null);

            assertNull(captureSaved().getEndpoint());
        }

        // UTCID03 - Normal: vẫn ghi nhận người thực hiện như hàm log thường
        @Test
        void logWithEndpoint_recordsActorAndAction() {
            auditLogService.logWithEndpoint(AuditAction.VERIFY_SUBSCRIPTION, "invoices", "12",
                    null, null, "PUT /api/admin/subscriptions/12/verify");

            AuditLog saved = captureSaved();
            assertEquals(1L, saved.getUser().getUserId());
            assertEquals(AuditAction.VERIFY_SUBSCRIPTION, saved.getAction());
            assertEquals("invoices", saved.getTableName());
        }
    }

    // =====================================================================
    // Function: getLogs
    // =====================================================================
    @Nested
    @DisplayName("getLogs")
    class GetLogsTest {

        private AuditLogFilterRequest filter(String sortBy, String sortDir, int page, int size) {
            AuditLogFilterRequest filter = new AuditLogFilterRequest();
            filter.setSortBy(sortBy);
            filter.setSortDir(sortDir);
            filter.setPage(page);
            filter.setSize(size);
            return filter;
        }

        // UTCID01 - Normal: sortDir = "desc" -> sắp xếp giảm dần theo thời gian tạo
        @Test
        void getLogs_sortDesc_appliesDescendingSort() {
            when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(Page.empty());

            auditLogService.getLogs(filter("createdAt", "desc", 0, 20));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(auditLogRepository).findAll(any(Specification.class), captor.capture());
            assertTrue(captor.getValue().getSort().getOrderFor("createdAt").isDescending());
        }

        // UTCID02 - Normal: sortDir = "asc" -> sắp xếp tăng dần
        @Test
        void getLogs_sortAsc_appliesAscendingSort() {
            when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(Page.empty());

            auditLogService.getLogs(filter("createdAt", "asc", 0, 20));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(auditLogRepository).findAll(any(Specification.class), captor.capture());
            assertTrue(captor.getValue().getSort().getOrderFor("createdAt").isAscending());
        }

        // UTCID03 - Normal: có kết quả -> map sang response trả về client
        @Test
        void getLogs_hasRecords_returnsMappedPage() {
            when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(new AuditLog(), new AuditLog())));
            when(auditLogMapper.toResponse(any())).thenReturn(new AuditLogResponse());

            Page<AuditLogResponse> result = auditLogService.getLogs(filter("createdAt", "desc", 0, 20));

            assertEquals(2, result.getTotalElements());
        }

        // UTCID04 - Boundary: không có bản ghi nào khớp bộ lọc -> trang rỗng
        @Test
        void getLogs_noRecords_returnsEmptyPage() {
            when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(Page.empty());

            assertTrue(auditLogService.getLogs(filter("createdAt", "desc", 0, 20)).isEmpty());
        }

        // UTCID05 - Boundary: phân trang trang 2, cỡ 50 -> truyền đúng xuống repository
        @Test
        void getLogs_customPaging_passesPageAndSize() {
            when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(Page.empty());

            auditLogService.getLogs(filter("createdAt", "desc", 2, 50));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(auditLogRepository).findAll(any(Specification.class), captor.capture());
            assertEquals(2, captor.getValue().getPageNumber());
            assertEquals(50, captor.getValue().getPageSize());
        }

        // UTCID06 - Abnormal: filter = null -> chặn ngay đầu vào
        @Test
        void getLogs_nullFilter_throwsEmptyFilter() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> auditLogService.getLogs(null));

            assertEquals("Bộ lọc nhật ký không được để trống", ex.getMessage());
            verify(auditLogRepository, never()).findAll(any(Specification.class), any(Pageable.class));
        }

        // UTCID07 - Abnormal: page = -1 -> số trang không hợp lệ
        @Test
        void getLogs_negativePage_throwsInvalidPage() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> auditLogService.getLogs(filter("createdAt", "desc", -1, 20)));

            assertEquals("Số trang không được nhỏ hơn 0", ex.getMessage());
            verify(auditLogRepository, never()).findAll(any(Specification.class), any(Pageable.class));
        }

        // UTCID08 - Boundary: size = 0 -> kích thước trang phải lớn hơn 0
        @Test
        void getLogs_zeroSize_throwsInvalidSize() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> auditLogService.getLogs(filter("createdAt", "desc", 0, 0)));

            assertEquals("Kích thước trang phải lớn hơn 0", ex.getMessage());
            verify(auditLogRepository, never()).findAll(any(Specification.class), any(Pageable.class));
        }

        // UTCID09 - Boundary: size = 101 -> vượt trần 100 bản ghi mỗi trang
        @Test
        void getLogs_sizeOverLimit_throwsSizeExceeded() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> auditLogService.getLogs(filter("createdAt", "desc", 0, 101)));

            assertEquals("Kích thước trang không được vượt quá 100", ex.getMessage());
            verify(auditLogRepository, never()).findAll(any(Specification.class), any(Pageable.class));
        }

        // UTCID10 - Abnormal: sortBy = "oldValue" ngoài danh sách cho phép -> chặn dò tên cột
        @Test
        void getLogs_sortByNotAllowed_throwsUnsupportedSortField() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> auditLogService.getLogs(filter("oldValue", "desc", 0, 20)));

            assertEquals("Không hỗ trợ sắp xếp theo trường: oldValue", ex.getMessage());
            verify(auditLogRepository, never()).findAll(any(Specification.class), any(Pageable.class));
        }

        // UTCID11 - Abnormal: from 12/08/2026 sau to 01/08/2026 -> khoảng thời gian ngược
        @Test
        void getLogs_fromAfterTo_throwsInvalidRange() {
            AuditLogFilterRequest request = filter("createdAt", "desc", 0, 20);
            request.setFrom(LocalDateTime.of(2026, 8, 12, 0, 0));
            request.setTo(LocalDateTime.of(2026, 8, 1, 0, 0));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> auditLogService.getLogs(request));

            assertEquals("Thời gian bắt đầu phải trước thời gian kết thúc", ex.getMessage());
            verify(auditLogRepository, never()).findAll(any(Specification.class), any(Pageable.class));
        }
    }
}
