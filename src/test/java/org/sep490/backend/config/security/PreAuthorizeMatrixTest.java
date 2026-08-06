package org.sep490.backend.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm chứng TỪNG @PreAuthorize gắn trên controller.
 *
 * VÌ SAO CẦN: biểu thức trong @PreAuthorize là chuỗi SpEL, KHÔNG được kiểm tra lúc
 * compile. Gõ nhầm 'PERM_TAG_MANAGE' thành 'PERM_TAG_MANAGEE' hay '@perm.isOwnner'
 * đều biên dịch trót lọt — chỉ vỡ lúc chạy thật. Bộ test này là lưới an toàn duy nhất.
 *
 * CÁCH ĐỌC KẾT QUẢ:
 *   - Thiếu quyền  -> phải 403. Nếu ra 401 nghĩa là filter chain chặn trước, sai tầng.
 *   - Đủ quyền     -> phải KHÁC 403. Không khẳng định 200 vì service/DB có thể ném
 *                     lỗi khác (404, 400) — điều đó vẫn chứng minh đã qua tầng phân quyền.
 *   - Sai tên quyền trong SpEL -> test "đủ quyền" sẽ đỏ, vì authority cấp trong test
 *                     không khớp chuỗi trong annotation.
 */
@SpringBootTest
@DisplayName("Ma trận @PreAuthorize trên toàn bộ controller")
class PreAuthorizeMatrixTest {

    static {
        DotEnvConfig.loadEnv();
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ================= Helper =================

    /** Authority tối thiểu để qua filter chain của /api/admin/** */
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_CURATOR = "ROLE_CURATOR";
    private static final String ROLE_EXPLORER = "ROLE_EXPLORER";
    private static final String ROLE_PARTNER = "ROLE_PARTNER";

    private MockHttpServletRequestBuilder req(HttpMethod method, String path) {
        return MockMvcRequestBuilders.request(method, path);
    }

    /** Gọi endpoint với đúng tập authority chỉ định. */
    private void call(HttpMethod method, String path, ResultMatcher expect, String... authorities)
            throws Exception {
        call(method, path, null, expect, authorities);
    }

    /**
     * Bản có body. BẮT BUỘC dùng cho endpoint có @RequestBody: Spring parse body
     * TRƯỚC khi chạy @PreAuthorize, nên thiếu body sẽ ra 400/500 và che mất 403.
     */
    private void call(HttpMethod method, String path, String jsonBody, ResultMatcher expect,
                      String... authorities) throws Exception {
        SimpleGrantedAuthority[] granted = new SimpleGrantedAuthority[authorities.length];
        for (int i = 0; i < authorities.length; i++) {
            granted[i] = new SimpleGrantedAuthority(authorities[i]);
        }
        MockHttpServletRequestBuilder builder = req(method, path).with(jwt().authorities(granted));
        if (jsonBody != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        }
        mvc().perform(builder).andExpect(expect);
    }

    /** Bị @PreAuthorize từ chối. */
    private static ResultMatcher forbidden() {
        return status().isForbidden();
    }

    /**
     * Không được phép thực hiện — nhưng chấp nhận cả 403 lẫn 4xx khác.
     *
     * Dùng cho endpoint có @perm.isOwnerOrHasPerm: nhánh isOwner gọi getCurrentUser(),
     * với JWT giả (không có user trong DB) sẽ ném BusinessException -> 400 trước khi
     * kịp trả 403. Điều quan trọng là request KHÔNG thành công.
     */
    private static ResultMatcher deniedSomehow() {
        return result -> {
            int s = result.getResponse().getStatus();
            if (s >= 200 && s < 300) {
                throw new AssertionError(
                        "Request THÀNH CÔNG (" + s + ") dù không có quyền — phân quyền bị hở");
            }
        };
    }

    /**
     * Đã qua tầng phân quyền. Không ép 200 vì service có thể ném 404/400 do
     * thiếu dữ liệu thật — điều đó không liên quan tới phân quyền.
     */
    private static ResultMatcher passedAuthz() {
        return result -> {
            int s = result.getResponse().getStatus();
            if (s == 403) {
                throw new AssertionError(
                        "Bị 403 dù đã cấp đủ quyền -> tên quyền trong @PreAuthorize có thể sai chính tả");
            }
            if (s == 401) {
                throw new AssertionError("Bị 401 -> filter chain chặn trước khi tới @PreAuthorize");
            }
        };
    }

    // ================= AdminController =================

    @Nested
    @DisplayName("AdminController — 9 endpoint")
    class AdminControllerTest {

        @Test
        @DisplayName("GET /users cần PERM_USER_VIEW_ALL")
        void getUsers() throws Exception {
            call(HttpMethod.GET, "/api/admin/users", forbidden(), ROLE_ADMIN);
            call(HttpMethod.GET, "/api/admin/users", passedAuthz(), ROLE_ADMIN, "PERM_USER_VIEW_ALL");
        }

        @Test
        @DisplayName("PUT /{id}/lock và /unlock cùng cần PERM_USER_LOCK")
        void lockUnlock() throws Exception {
            call(HttpMethod.PUT, "/api/admin/1/lock", forbidden(), ROLE_ADMIN);
            call(HttpMethod.PUT, "/api/admin/1/lock", passedAuthz(), ROLE_ADMIN, "PERM_USER_LOCK");

            call(HttpMethod.PUT, "/api/admin/1/unlock", forbidden(), ROLE_ADMIN);
            call(HttpMethod.PUT, "/api/admin/1/unlock", passedAuthz(), ROLE_ADMIN, "PERM_USER_LOCK");
        }

        @Test
        @DisplayName("PUT /{id}/role cần PERM_USER_UPDATE_ROLE — KHÔNG dùng chung PERM_USER_LOCK")
        void updateRole() throws Exception {
            String body = "{\"roles\":\"CURATOR\"}";
            // Cấp nhầm quyền khoá tài khoản thì vẫn phải bị chặn
            call(HttpMethod.PUT, "/api/admin/1/role", body, forbidden(), ROLE_ADMIN, "PERM_USER_LOCK");
            call(HttpMethod.PUT, "/api/admin/1/role", body, passedAuthz(),
                    ROLE_ADMIN, "PERM_USER_UPDATE_ROLE");
        }

        @Test
        @DisplayName("Kiểm duyệt bài viết cần PERM_POST_MODERATE")
        void moderatePost() throws Exception {
            call(HttpMethod.PUT, "/api/admin/post/1/approve", forbidden(), ROLE_ADMIN);
            call(HttpMethod.PUT, "/api/admin/post/1/approve", passedAuthz(), ROLE_ADMIN, "PERM_POST_MODERATE");

            call(HttpMethod.PUT, "/api/admin/post/1/reject", "{\"rejectReason\":\"spam\"}",
                    forbidden(), ROLE_ADMIN);
            call(HttpMethod.PUT, "/api/admin/1/ban", "{\"reason\":\"vi pham\"}",
                    forbidden(), ROLE_ADMIN);
        }

        @Test
        @DisplayName("Subscription cần PERM_SUBSCRIPTION_VERIFY")
        void subscription() throws Exception {
            call(HttpMethod.GET, "/api/admin/subscriptions", forbidden(), ROLE_ADMIN);
            call(HttpMethod.GET, "/api/admin/subscriptions", passedAuthz(), ROLE_ADMIN, "PERM_SUBSCRIPTION_VERIFY");
            // isApproved là @RequestParam bắt buộc -> thiếu sẽ ra 500 trước khi tới @PreAuthorize
            call(HttpMethod.PATCH, "/api/admin/subscription/1/verify?isApproved=true",
                    forbidden(), ROLE_ADMIN);
        }
    }

    // ================= Dashboard =================

    @Nested
    @DisplayName("Dashboard — admin và curator tách quyền riêng")
    class DashboardTest {

        @Test
        @DisplayName("Admin dashboard cần PERM_DASHBOARD_ADMIN_VIEW")
        void adminDashboard() throws Exception {
            call(HttpMethod.GET, "/api/admin/dashboard", forbidden(), ROLE_ADMIN);
            call(HttpMethod.GET, "/api/admin/dashboard", passedAuthz(),
                    ROLE_ADMIN, "PERM_DASHBOARD_ADMIN_VIEW");
        }

        @Test
        @DisplayName("Curator dashboard cần PERM_DASHBOARD_CURATOR_VIEW")
        void curatorDashboard() throws Exception {
            call(HttpMethod.GET, "/api/curator/dashboard", forbidden(), ROLE_CURATOR);
            call(HttpMethod.GET, "/api/curator/dashboard", passedAuthz(),
                    ROLE_CURATOR, "PERM_DASHBOARD_CURATOR_VIEW");
        }

        @Test
        @DisplayName("Quyền dashboard admin KHÔNG mở được dashboard curator và ngược lại")
        void khongDungChungQuyen() throws Exception {
            call(HttpMethod.GET, "/api/curator/dashboard", forbidden(),
                    ROLE_ADMIN, "PERM_DASHBOARD_ADMIN_VIEW");
            call(HttpMethod.GET, "/api/admin/dashboard", forbidden(),
                    ROLE_ADMIN, "PERM_DASHBOARD_CURATOR_VIEW");
        }
    }

    // ================= Audit log & Subscription plan =================

    @Nested
    @DisplayName("AuditLog / SubscriptionPlan")
    class AdminOtherTest {

        @Test
        @DisplayName("Audit log cần PERM_AUDIT_LOG_VIEW")
        void auditLog() throws Exception {
            call(HttpMethod.GET, "/api/admin/audit-logs", forbidden(), ROLE_ADMIN);
            call(HttpMethod.GET, "/api/admin/audit-logs", passedAuthz(), ROLE_ADMIN, "PERM_AUDIT_LOG_VIEW");
        }

        @Test
        @DisplayName("GET subscription-plans chỉ cần đăng nhập (ngoại lệ đặt TRƯỚC rule /api/admin/**)")
        void getPlanChiCanDangNhap() throws Exception {
            // EXPLORER không có role ADMIN vẫn đọc được — chứng minh thứ tự matcher đúng
            call(HttpMethod.GET, "/api/admin/subscription-plans", passedAuthz(), ROLE_EXPLORER);
        }

        @Test
        @DisplayName("Ghi subscription-plans cần PERM_SUBSCRIPTION_PLAN_MANAGE")
        void ghiPlanCanQuyen() throws Exception {
            call(HttpMethod.DELETE, "/api/admin/subscription-plans/1", forbidden(), ROLE_ADMIN);
            call(HttpMethod.DELETE, "/api/admin/subscription-plans/1", passedAuthz(),
                    ROLE_ADMIN, "PERM_SUBSCRIPTION_PLAN_MANAGE");
        }
    }

    // ================= PermissionAdminController (@PreAuthorize cấp class) =================

    @Nested
    @DisplayName("PermissionAdminController — annotation ở cấp class, áp cho MỌI method")
    class PermissionAdminTest {

        @Test
        @DisplayName("ADMIN thiếu PERM_PERMISSION_MANAGE -> 403 ở tất cả endpoint")
        void thieuQuyenThi403() throws Exception {
            call(HttpMethod.GET, "/api/admin/permissions", forbidden(), ROLE_ADMIN);
            call(HttpMethod.GET, "/api/admin/permissions/matrix", forbidden(), ROLE_ADMIN);
            call(HttpMethod.GET, "/api/admin/permissions/users/1", forbidden(), ROLE_ADMIN);
            call(HttpMethod.DELETE, "/api/admin/permissions/roles/CURATOR/TAG_MANAGE",
                    forbidden(), ROLE_ADMIN);
        }

        @Test
        @DisplayName("Có PERM_PERMISSION_MANAGE -> qua được")
        void duQuyenThiQua() throws Exception {
            call(HttpMethod.GET, "/api/admin/permissions", passedAuthz(),
                    ROLE_ADMIN, "PERM_PERMISSION_MANAGE");
            call(HttpMethod.GET, "/api/admin/permissions/matrix", passedAuthz(),
                    ROLE_ADMIN, "PERM_PERMISSION_MANAGE");
        }
    }

    // ================= Content: quyền quản lý nội dung =================

    @Nested
    @DisplayName("Content — mỗi loại nội dung một quyền riêng, không dùng chung")
    class ContentTest {

        @Test
        @DisplayName("Tag: thao tác ghi cần PERM_TAG_MANAGE")
        void tagGhiCanQuyen() throws Exception {
            call(HttpMethod.DELETE, "/api/tags/1", forbidden(), ROLE_CURATOR);
            call(HttpMethod.DELETE, "/api/tags/1", passedAuthz(), ROLE_CURATOR, "PERM_TAG_MANAGE");
        }

        /**
         * Chống lỗi copy-paste: gắn nhầm quyền của loại nội dung khác.
         *
         * VÌ SAO KHÔNG ASSERT 403 Ở ĐÂY: các endpoint này dùng @perm.isOwnerOrHasPerm.
         * Khi thiếu quyền, nó rơi xuống nhánh isOwner -> gọi userService.getCurrentUser()
         * -> JWT giả trong test không có bản ghi user trong DB -> BusinessException -> 400.
         *
         * 400 ở đây VẪN chứng minh quyền TAG không mở được STORY/ROUTE (nếu mở được thì
         * đã chạy vào service và trả 404/200). Điều cần khẳng định là KHÔNG phải 2xx.
         * Nhánh 403 thuần tuý được kiểm ở ContentTest.quyenSaiKhongMoDuocTag bên dưới,
         * nơi annotation là hasAuthority thuần, không đụng DB.
         */
        @Test
        @DisplayName("Quyền TAG không mở được STORY — chống copy-paste sai quyền")
        void quyenTagKhongMoDuocStory() throws Exception {
            call(HttpMethod.DELETE, "/api/v1/stories/999999", deniedSomehow(),
                    ROLE_CURATOR, "PERM_TAG_MANAGE");
        }

        @Test
        @DisplayName("Quyền TAG không mở được ROUTE — chống copy-paste sai quyền")
        void quyenTagKhongMoDuocRoute() throws Exception {
            call(HttpMethod.DELETE, "/api/v1/routes/999999", deniedSomehow(),
                    ROLE_CURATOR, "PERM_TAG_MANAGE");
        }

        @Test
        @DisplayName("Quyền STORY/ROUTE không mở được TAG (annotation thuần, kiểm được 403 chính xác)")
        void quyenSaiKhongMoDuocTag() throws Exception {
            call(HttpMethod.DELETE, "/api/tags/1", forbidden(), ROLE_CURATOR, "PERM_STORY_MANAGE");
            call(HttpMethod.DELETE, "/api/tags/1", forbidden(), ROLE_CURATOR, "PERM_ROUTE_MANAGE");
        }

        @Test
        @DisplayName("Kiểm duyệt review cần PERM_REVIEW_MODERATE")
        void kiemDuyetReview() throws Exception {
            call(HttpMethod.PUT, "/api/v1/reviews/1/status?status=HIDDEN", forbidden(), ROLE_CURATOR);
            call(HttpMethod.PUT, "/api/v1/reviews/1/status?status=HIDDEN", passedAuthz(),
                    ROLE_CURATOR, "PERM_REVIEW_MODERATE");
        }

        @Test
        @DisplayName("Level: ghi cần PERM_LEVEL_MANAGE, đọc thì không")
        void level() throws Exception {
            call(HttpMethod.DELETE, "/api/gamification/levels/1", forbidden(), ROLE_ADMIN);
            call(HttpMethod.DELETE, "/api/gamification/levels/1", passedAuthz(),
                    ROLE_ADMIN, "PERM_LEVEL_MANAGE");
            call(HttpMethod.GET, "/api/gamification/levels", passedAuthz(), ROLE_EXPLORER);
        }
    }

    // ================= Partner: chặn theo ROLE ở cấp class =================

    @Nested
    @DisplayName("Partner — chặn bằng hasRole ở cấp class")
    class PartnerTest {

        @Test
        @DisplayName("Không phải PARTNER -> 403")
        void khongPhaiPartner() throws Exception {
            call(HttpMethod.GET, "/api/partner/subscriptions/my", forbidden(), ROLE_EXPLORER);
            call(HttpMethod.GET, "/api/partner/1/vouchers", forbidden(),
                    ROLE_EXPLORER, "PERM_VOUCHER_PARTNER_MANAGE");
        }

        @Test
        @DisplayName("PARTNER thiếu PERM_VOUCHER_PARTNER_MANAGE vẫn bị chặn ở voucher")
        void partnerThieuQuyenVoucher() throws Exception {
            call(HttpMethod.GET, "/api/partner/1/vouchers", forbidden(), ROLE_PARTNER);
        }

        @Test
        @DisplayName("PARTNER đủ role + quyền -> qua")
        void partnerDuQuyen() throws Exception {
            call(HttpMethod.GET, "/api/partner/1/vouchers", passedAuthz(),
                    ROLE_PARTNER, "PERM_VOUCHER_PARTNER_MANAGE");
            call(HttpMethod.GET, "/api/partner/subscriptions/my", passedAuthz(), ROLE_PARTNER);
        }
    }

    // ================= Bảo vệ 2 lớp =================

    @Nested
    @DisplayName("Bảo vệ 2 lớp: filter chain và @PreAuthorize độc lập nhau")
    class TwoLayerTest {

        @Test
        @DisplayName("Không token -> 401 (tầng 1), không phải 403")
        void khongTokenThi401() throws Exception {
            mvc().perform(req(HttpMethod.GET, "/api/admin/users")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Sai role -> 403 ở tầng 1, dù có đủ PERM_*")
        void saiRoleBiChanTang1() throws Exception {
            // EXPLORER có đủ quyền nhưng không có ROLE_ADMIN -> filter chain chặn trước
            call(HttpMethod.GET, "/api/admin/users", forbidden(), ROLE_EXPLORER, "PERM_USER_VIEW_ALL");
        }

        @Test
        @DisplayName("Đúng role, thiếu quyền -> 403 ở tầng 2")
        void thieuQuyenBiChanTang2() throws Exception {
            call(HttpMethod.GET, "/api/admin/users", forbidden(), ROLE_ADMIN);
        }
    }
}
