package org.sep490.backend.config.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chứng minh hành vi của filter chain với các endpoint CHƯA gắn @PreAuthorize.
 *
 * Câu hỏi: gọi mà không có token thì bị chặn hay đi lọt?
 * Kết luận: .anyRequest().authenticated() bắt hết -> 401. Không endpoint nào lọt.
 */
@SpringBootTest
class FilterChainRuleTest {

    static {
        // Giống BackEndApplicationTests: nạp .env trước khi Spring resolve placeholder
        DotEnvConfig.loadEnv();
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ===== Nhóm 1: endpoint chưa gắn @PreAuthorize =====

    @Test
    @DisplayName("Chưa gắn @PreAuthorize + không token -> 401, KHÔNG lọt")
    void chuaGanAnnotationKhongTokenThi401() throws Exception {
        MockMvc mvc = mockMvc();
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/saved-routes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/groups")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/vouchers/available")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Chưa gắn @PreAuthorize + có token bất kỳ -> qua tầng xác thực")
    void chuaGanAnnotationCoTokenThiQua() throws Exception {
        // EXPLORER không có PERM_* đặc biệt nào vẫn gọi được,
        // vì rule cuối chỉ yêu cầu .authenticated()
        mockMvc().perform(get("/api/notifications")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EXPLORER"))))
                .andExpect(not401());
    }

    // ===== Nhóm 2: PUBLIC_GET_ENDPOINTS — GET mở, ghi bị chặn =====

    @Test
    @DisplayName("GET nội dung public -> không cần token")
    void getNoiDungPublicKhongCanToken() throws Exception {
        mockMvc().perform(get("/api/tags")).andExpect(not401());
    }

    @Test
    @DisplayName("POST lên path public-GET + không token -> 401 (trước Phase 1 là 500)")
    void ghiLenPathPublicGetVanBiChan() throws Exception {
        mockMvc().perform(post("/api/posts")).andExpect(status().isUnauthorized());
    }

    // ===== Nhóm 3: admin — chặn 2 lớp =====

    @Test
    @DisplayName("Admin API: không token -> 401")
    void adminKhongTokenThi401() throws Exception {
        mockMvc().perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin API: sai role -> 403 (chặn ở filter chain)")
    void adminSaiRoleThi403() throws Exception {
        mockMvc().perform(get("/api/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EXPLORER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin API: đúng role nhưng THIẾU permission -> 403 (chặn ở @PreAuthorize)")
    void adminThieuPermissionThi403() throws Exception {
        mockMvc().perform(get("/api/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("Body 401 trả về là JSON parse được (đúng endpoint đã lỗi trên Swagger)")
    void body401LaJsonHopLe() throws Exception {
        String body = mockMvc().perform(delete("/api/v1/hotspots/5"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Phải parse được — trước khi sửa, chuỗi là { tatus":401 và Jackson ném lỗi
        JsonNode json = new ObjectMapper().readTree(body);
        assertThat(json.get("status").asInt()).isEqualTo(401);
        assertThat(json.get("errorCode").asText()).isEqualTo("UNAUTHORIZED");

        // Đi qua ObjectMapper THẬT của Spring: timestamp phải là chuỗi ISO, không phải
        // mảng [2026,8,6,...] như khi SecurityResponseWriter tự new ObjectMapper()
        assertThat(json.get("timestamp").isTextual()).isTrue();
    }

    /** Bất kỳ status nào khác 401 — nghĩa là đã qua được tầng xác thực. */
    private static ResultMatcher not401() {
        return result -> {
            int s = result.getResponse().getStatus();
            if (s == 401) {
                throw new AssertionError("Bị chặn 401, nhưng lẽ ra phải qua được tầng xác thực");
            }
        };
    }
}
