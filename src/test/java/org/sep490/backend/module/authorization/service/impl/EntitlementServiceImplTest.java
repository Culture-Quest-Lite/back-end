package org.sep490.backend.module.authorization.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authorization.service.EntitlementCacheService;
import org.sep490.backend.module.user.service.UserService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho QUYỀN LỢI GÓI (Entitlement) — dùng trong SpEL @PreAuthorize.
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EntitlementServiceImplTest {

    @Mock private UserService userService;
    @Mock private EntitlementCacheService cacheService;

    @InjectMocks private EntitlementServiceImpl entitlementService;

    /** User hiện tại có id = 1, dùng làm key tra cứu rule trong cache. */
    private User currentUser(Boolean isPremium) {
        User user = new User();
        user.setUserId(1L);
        user.setIsPremium(isPremium);
        when(userService.getCurrentUser()).thenReturn(user);
        return user;
    }

    private void rules(Map<String, String> rules) {
        when(cacheService.getRules(1L)).thenReturn(rules);
    }

    // =====================================================================
    // Function: can
    // =====================================================================
    @Nested
    @DisplayName("can")
    class CanTest {

        // UTCID01 - Normal: rule bật "true" -> cho phép
        @Test
        void can_ruleIsTrue_returnsTrue() {
            currentUser(true);
            rules(Map.of("AI_SUGGESTION", "true"));

            assertTrue(entitlementService.can("AI_SUGGESTION"));
        }

        // UTCID02 - Abnormal: rule tắt "false" -> từ chối
        @Test
        void can_ruleIsFalse_returnsFalse() {
            currentUser(false);
            rules(Map.of("AI_SUGGESTION", "false"));

            assertFalse(entitlementService.can("AI_SUGGESTION"));
        }

        // UTCID03 - Abnormal: gói không khai báo rule -> mặc định từ chối
        @Test
        void can_ruleMissing_defaultsToFalse() {
            currentUser(false);
            rules(Map.of("OTHER_RULE", "true"));

            assertFalse(entitlementService.can("AI_SUGGESTION"));
        }

        // UTCID04 - Boundary: giá trị rác không parse được -> từ chối (fail-safe)
        @Test
        void can_garbageValue_returnsFalse() {
            currentUser(true);
            rules(Map.of("AI_SUGGESTION", "yes"));

            assertFalse(entitlementService.can("AI_SUGGESTION"));
        }

        // UTCID05 - Boundary: "TRUE" viết hoa -> vẫn được chấp nhận
        @Test
        void can_uppercaseTrue_returnsTrue() {
            currentUser(true);
            rules(Map.of("AI_SUGGESTION", "TRUE"));

            assertTrue(entitlementService.can("AI_SUGGESTION"));
        }
    }

    // =====================================================================
    // Function: withinQuota
    // =====================================================================
    @Nested
    @DisplayName("withinQuota")
    class WithinQuotaTest {

        // UTCID01 - Abnormal: gói không khai báo hạn mức -> từ chối
        @Test
        void withinQuota_limitMissing_returnsFalse() {
            currentUser(false);
            rules(Map.of("OTHER_RULE", "10"));

            assertFalse(entitlementService.withinQuota("PLAN_LIMIT", 0));
        }

        // UTCID02 - Abnormal: hạn mức không phải số -> từ chối (fail-safe)
        @Test
        void withinQuota_nonNumericLimit_returnsFalse() {
            currentUser(false);
            rules(Map.of("PLAN_LIMIT", "khong-gioi-han"));

            assertFalse(entitlementService.withinQuota("PLAN_LIMIT", 0));
        }

        // UTCID03 - Normal: đã dùng 3/10 -> còn quota
        @Test
        void withinQuota_usedBelowLimit_returnsTrue() {
            currentUser(false);
            rules(Map.of("PLAN_LIMIT", "10"));

            assertTrue(entitlementService.withinQuota("PLAN_LIMIT", 3));
        }

        // UTCID04 - Boundary: đã dùng đúng 9/10 -> vẫn còn 1 slot cuối
        @Test
        void withinQuota_usedIsLimitMinusOne_returnsTrue() {
            currentUser(false);
            rules(Map.of("PLAN_LIMIT", "10"));

            assertTrue(entitlementService.withinQuota("PLAN_LIMIT", 9));
        }

        // UTCID05 - Boundary: đã dùng đúng 10/10 -> hết quota (điều kiện used < limit)
        @Test
        void withinQuota_usedEqualsLimit_returnsFalse() {
            currentUser(false);
            rules(Map.of("PLAN_LIMIT", "10"));

            assertFalse(entitlementService.withinQuota("PLAN_LIMIT", 10));
        }

        // UTCID06 - Boundary: hạn mức = 0 -> chặn ngay từ lần đầu
        @Test
        void withinQuota_zeroLimit_blocksFirstUse() {
            currentUser(false);
            rules(Map.of("PLAN_LIMIT", "0"));

            assertFalse(entitlementService.withinQuota("PLAN_LIMIT", 0));
        }
    }

    // =====================================================================
    // Function: isPremium
    // =====================================================================
    @Nested
    @DisplayName("isPremium")
    class IsPremiumTest {

        // UTCID01 - Normal: tài khoản premium
        @Test
        void isPremium_premiumUser_returnsTrue() {
            currentUser(true);

            assertTrue(entitlementService.isPremium());
        }

        // UTCID02 - Normal: tài khoản thường
        @Test
        void isPremium_normalUser_returnsFalse() {
            currentUser(false);

            assertFalse(entitlementService.isPremium());
        }

        // UTCID03 - Boundary: isPremium = null (user cũ chưa migrate) -> không NPE, trả false
        @Test
        void isPremium_nullFlag_returnsFalseWithoutNpe() {
            currentUser(null);

            assertFalse(entitlementService.isPremium());
        }
    }
}
