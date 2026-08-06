package org.sep490.backend.module.admin.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.admin.dto.request.SubscriptionPlanRequest;
import org.sep490.backend.module.admin.dto.response.SubscriptionPlanResponse;
import org.sep490.backend.module.admin.entity.PlanRule;
import org.sep490.backend.module.admin.entity.SubscriptionPlan;
import org.sep490.backend.module.admin.entity.enumeration.PlanType;
import org.sep490.backend.module.admin.entity.enumeration.SubscriptionPlanStatus;
import org.sep490.backend.module.admin.mapper.SubscriptionPlanMapper;
import org.sep490.backend.module.admin.repository.PlanRuleRepository;
import org.sep490.backend.module.admin.repository.SubscriptionPlanRepository;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho GÓI DỊCH VỤ (Subscription Plan).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionPlanServiceImplTest {

    @Mock private SubscriptionPlanMapper subscriptionPlanMapper;
    @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock private PlanRuleRepository planRuleRepository;

    @InjectMocks private SubscriptionPlanServiceImpl subscriptionPlanService;

    private static SubscriptionPlan plan(Long id, SubscriptionPlanStatus status, PlanType type) {
        return SubscriptionPlan.builder()
                .subscriptionPlanId(id)
                .subscriptionPlanName("Gói CHUẨN")
                .priceMonthly(200000L)
                .priceYearly(2000000L)
                .planType(type)
                .status(status)
                .build();
    }

    private static SubscriptionPlanRequest request(String name, Map<String, Object> configLimit) {
        SubscriptionPlanRequest request = new SubscriptionPlanRequest();
        request.setSubscriptionPlanName(name);
        request.setSubscriptionPlanDescription("Gói dành cho quán vừa và nhỏ");
        request.setPriceMonthly(200000L);
        request.setPriceYearly(2000000L);
        request.setConfigLimit(configLimit);
        return request;
    }

    // =====================================================================
    // Function: createSubscriptionPlan
    // =====================================================================
    @Nested
    @DisplayName("createSubscriptionPlan")
    class CreateSubscriptionPlanTest {

        // UTCID01 - Abnormal: tên gói đã tồn tại (không phân biệt hoa thường)
        @Test
        void createSubscriptionPlan_duplicateName_throwsNameExists() {
            when(subscriptionPlanRepository.existsBySubscriptionPlanNameIgnoreCase("Gói CHUẨN"))
                    .thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> subscriptionPlanService.createSubscriptionPlan(
                            request("Gói CHUẨN", Map.of())));

            assertEquals("Gói dịch vụ với tên \"Gói CHUẨN\" đã tồn tại", ex.getMessage());
            verify(subscriptionPlanRepository, never()).save(any());
        }

        // UTCID02 - Normal: không truyền planType -> mặc định PARTNER, status ACTIVE
        @Test
        void createSubscriptionPlan_nullPlanType_defaultsToPartner() {
            SubscriptionPlan mapped = plan(null, null, null);
            when(subscriptionPlanRepository.existsBySubscriptionPlanNameIgnoreCase(anyString()))
                    .thenReturn(false);
            when(subscriptionPlanMapper.toEntity(any())).thenReturn(mapped);
            when(subscriptionPlanRepository.save(any(SubscriptionPlan.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            subscriptionPlanService.createSubscriptionPlan(request("Gói CHUẨN", null));

            assertEquals(PlanType.PARTNER, mapped.getPlanType());
            assertEquals(SubscriptionPlanStatus.ACTIVE, mapped.getStatus());
        }

        // UTCID03 - Normal: có truyền planType PREMIUM -> giữ nguyên, không ghi đè
        @Test
        void createSubscriptionPlan_explicitPlanType_isKept() {
            SubscriptionPlan mapped = plan(null, null, PlanType.PREMIUM);
            when(subscriptionPlanRepository.existsBySubscriptionPlanNameIgnoreCase(anyString()))
                    .thenReturn(false);
            when(subscriptionPlanMapper.toEntity(any())).thenReturn(mapped);
            when(subscriptionPlanRepository.save(any(SubscriptionPlan.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            subscriptionPlanService.createSubscriptionPlan(request("Gói PREMIUM", null));

            assertEquals(PlanType.PREMIUM, mapped.getPlanType());
        }

        // UTCID04 - Normal: có configLimit -> sinh PlanRule tương ứng cho từng key
        @Test
        void createSubscriptionPlan_withConfigLimit_createsPlanRules() {
            SubscriptionPlan mapped = plan(1L, null, PlanType.PARTNER);
            when(subscriptionPlanRepository.existsBySubscriptionPlanNameIgnoreCase(anyString()))
                    .thenReturn(false);
            when(subscriptionPlanMapper.toEntity(any())).thenReturn(mapped);
            when(subscriptionPlanRepository.save(any(SubscriptionPlan.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(planRuleRepository.findBySubscriptionPlan_SubscriptionPlanId(1L)).thenReturn(List.of());

            subscriptionPlanService.createSubscriptionPlan(
                    request("Gói CHUẨN", Map.of("VOUCHER_LIMIT", 10)));

            ArgumentCaptor<PlanRule> captor = ArgumentCaptor.forClass(PlanRule.class);
            verify(planRuleRepository).save(captor.capture());
            assertEquals("VOUCHER_LIMIT", captor.getValue().getRuleKey());
            assertEquals("10", captor.getValue().getRuleValue());
            assertEquals("Giới hạn cho VOUCHER_LIMIT", captor.getValue().getDescription());
        }

        // UTCID05 - Boundary: configLimit rỗng -> không tạo PlanRule nào
        @Test
        void createSubscriptionPlan_emptyConfigLimit_createsNoRules() {
            SubscriptionPlan mapped = plan(1L, null, PlanType.PARTNER);
            when(subscriptionPlanRepository.existsBySubscriptionPlanNameIgnoreCase(anyString()))
                    .thenReturn(false);
            when(subscriptionPlanMapper.toEntity(any())).thenReturn(mapped);
            when(subscriptionPlanRepository.save(any(SubscriptionPlan.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(planRuleRepository.findBySubscriptionPlan_SubscriptionPlanId(anyLong()))
                    .thenReturn(List.of());

            subscriptionPlanService.createSubscriptionPlan(request("Gói CHUẨN", Map.of()));

            verify(planRuleRepository, never()).save(any());
        }
    }

    // =====================================================================
    // Function: updateSubscriptionPlan
    // =====================================================================
    @Nested
    @DisplayName("updateSubscriptionPlan")
    class UpdateSubscriptionPlanTest {

        // UTCID01 - Abnormal: gói không tồn tại
        @Test
        void updateSubscriptionPlan_notFound_throwsNotFound() {
            when(subscriptionPlanRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> subscriptionPlanService.updateSubscriptionPlan(1L,
                            request("Gói MỚI", null)));

            assertEquals("Không tìm thấy gói dịch vụ với id: 1", ex.getMessage());
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        }

        // UTCID02 - Abnormal: gói đang bị vô hiệu hóa -> không cho cập nhật
        @Test
        void updateSubscriptionPlan_inactivePlan_throwsCannotUpdate() {
            when(subscriptionPlanRepository.findById(1L))
                    .thenReturn(Optional.of(plan(1L, SubscriptionPlanStatus.INACTIVE, PlanType.PARTNER)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> subscriptionPlanService.updateSubscriptionPlan(1L,
                            request("Gói MỚI", null)));

            assertEquals("Gói dịch vụ đang bị vô hiệu hóa, không thể cập nhật", ex.getMessage());
            verify(subscriptionPlanRepository, never()).save(any());
        }

        // UTCID03 - Abnormal: tên mới trùng với gói khác
        @Test
        void updateSubscriptionPlan_duplicateNameOnOtherPlan_throwsNameExists() {
            when(subscriptionPlanRepository.findById(1L))
                    .thenReturn(Optional.of(plan(1L, SubscriptionPlanStatus.ACTIVE, PlanType.PARTNER)));
            when(subscriptionPlanRepository
                    .existsBySubscriptionPlanNameIgnoreCaseAndSubscriptionPlanIdNot("Gói VIP", 1L))
                    .thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> subscriptionPlanService.updateSubscriptionPlan(1L, request("Gói VIP", null)));

            assertEquals("Gói dịch vụ với tên \"Gói VIP\" đã tồn tại", ex.getMessage());
        }

        // UTCID04 - Normal: cập nhật hợp lệ -> xóa rule cũ, tạo lại rule mới
        @Test
        void updateSubscriptionPlan_valid_replacesOldPlanRules() {
            SubscriptionPlan target = plan(1L, SubscriptionPlanStatus.ACTIVE, PlanType.PARTNER);
            List<PlanRule> oldRules = List.of(PlanRule.builder().ruleKey("CU").ruleValue("1").build());
            when(subscriptionPlanRepository.findById(1L)).thenReturn(Optional.of(target));
            when(subscriptionPlanRepository
                    .existsBySubscriptionPlanNameIgnoreCaseAndSubscriptionPlanIdNot(anyString(), anyLong()))
                    .thenReturn(false);
            when(subscriptionPlanRepository.save(any(SubscriptionPlan.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(planRuleRepository.findBySubscriptionPlan_SubscriptionPlanId(1L)).thenReturn(oldRules);

            SubscriptionPlanRequest request = request("Gói CHUẨN", Map.of("VOUCHER_LIMIT", 20));
            SubscriptionPlanResponse expected = mock(SubscriptionPlanResponse.class);
            when(subscriptionPlanMapper.toResponse(target)).thenReturn(expected);

            assertSame(expected, subscriptionPlanService.updateSubscriptionPlan(1L, request));

            verify(subscriptionPlanMapper).updateEntityFromRequest(request, target);
            verify(planRuleRepository).deleteAll(oldRules);
            verify(planRuleRepository).save(any(PlanRule.class));
        }

        // UTCID05 - Boundary: giá trị configLimit là null -> lưu chuỗi "0"
        @Test
        void updateSubscriptionPlan_nullConfigValue_savesZeroString() {
            SubscriptionPlan target = plan(1L, SubscriptionPlanStatus.ACTIVE, PlanType.PARTNER);
            when(subscriptionPlanRepository.findById(1L)).thenReturn(Optional.of(target));
            when(subscriptionPlanRepository
                    .existsBySubscriptionPlanNameIgnoreCaseAndSubscriptionPlanIdNot(anyString(), anyLong()))
                    .thenReturn(false);
            when(subscriptionPlanRepository.save(any(SubscriptionPlan.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(planRuleRepository.findBySubscriptionPlan_SubscriptionPlanId(anyLong()))
                    .thenReturn(List.of());

            java.util.Map<String, Object> configLimit = new java.util.HashMap<>();
            configLimit.put("VOUCHER_LIMIT", null);

            subscriptionPlanService.updateSubscriptionPlan(1L, request("Gói CHUẨN", configLimit));

            ArgumentCaptor<PlanRule> captor = ArgumentCaptor.forClass(PlanRule.class);
            verify(planRuleRepository).save(captor.capture());
            assertEquals("0", captor.getValue().getRuleValue());
        }
    }

    // =====================================================================
    // Function: getSubscriptionPlanById
    // =====================================================================
    @Nested
    @DisplayName("getSubscriptionPlanById")
    class GetSubscriptionPlanByIdTest {

        // UTCID01 - Abnormal: id không tồn tại -> 404
        @Test
        void getSubscriptionPlanById_notFound_throws404() {
            when(subscriptionPlanRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> subscriptionPlanService.getSubscriptionPlanById(99L));

            assertEquals("Không tìm thấy gói dịch vụ với id: 99", ex.getMessage());
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        }

        // UTCID02 - Abnormal: gói đã bị xóa mềm -> 404
        @Test
        void getSubscriptionPlanById_deletedPlan_throws404() {
            when(subscriptionPlanRepository.findById(1L))
                    .thenReturn(Optional.of(plan(1L, SubscriptionPlanStatus.DELETED, PlanType.PARTNER)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> subscriptionPlanService.getSubscriptionPlanById(1L));

            assertEquals("Gói dịch vụ với id 1 đã bị xóa", ex.getMessage());
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        }

        // UTCID03 - Normal: gói ACTIVE -> trả về entity
        @Test
        void getSubscriptionPlanById_activePlan_returnsPlan() {
            SubscriptionPlan target = plan(1L, SubscriptionPlanStatus.ACTIVE, PlanType.PARTNER);
            when(subscriptionPlanRepository.findById(1L)).thenReturn(Optional.of(target));

            assertSame(target, subscriptionPlanService.getSubscriptionPlanById(1L));
        }

        // UTCID04 - Boundary: gói INACTIVE vẫn đọc được (chỉ chặn khi cập nhật)
        @Test
        void getSubscriptionPlanById_inactivePlan_stillReadable() {
            SubscriptionPlan target = plan(1L, SubscriptionPlanStatus.INACTIVE, PlanType.PARTNER);
            when(subscriptionPlanRepository.findById(1L)).thenReturn(Optional.of(target));

            assertSame(target, subscriptionPlanService.getSubscriptionPlanById(1L));
        }
    }

    // =====================================================================
    // Function: deleteSubscriptionPlan
    // =====================================================================
    @Nested
    @DisplayName("deleteSubscriptionPlan")
    class DeleteSubscriptionPlanTest {

        // UTCID01 - Abnormal: gói không tồn tại
        @Test
        void deleteSubscriptionPlan_notFound_throws404() {
            when(subscriptionPlanRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> subscriptionPlanService.deleteSubscriptionPlan(99L));

            assertEquals("Không tìm thấy gói dịch vụ với id: 99", ex.getMessage());
        }

        // UTCID02 - Abnormal: gói đã bị xóa -> getSubscriptionPlanById chặn trước
        @Test
        void deleteSubscriptionPlan_alreadyDeleted_throws404() {
            when(subscriptionPlanRepository.findById(1L))
                    .thenReturn(Optional.of(plan(1L, SubscriptionPlanStatus.DELETED, PlanType.PARTNER)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> subscriptionPlanService.deleteSubscriptionPlan(1L));

            assertEquals("Gói dịch vụ với id 1 đã bị xóa", ex.getMessage());
            verify(subscriptionPlanRepository, never()).save(any());
        }

        // UTCID03 - Normal: xóa mềm -> status chuyển DELETED
        @Test
        void deleteSubscriptionPlan_activePlan_setsStatusDeleted() {
            SubscriptionPlan target = plan(1L, SubscriptionPlanStatus.ACTIVE, PlanType.PARTNER);
            when(subscriptionPlanRepository.findById(1L)).thenReturn(Optional.of(target));

            subscriptionPlanService.deleteSubscriptionPlan(1L);

            assertEquals(SubscriptionPlanStatus.DELETED, target.getStatus());
            verify(subscriptionPlanRepository).save(target);
        }
    }

    // =====================================================================
    // Function: getActivePlanByType
    // =====================================================================
    @Nested
    @DisplayName("getActivePlanByType")
    class GetActivePlanByTypeTest {

        // UTCID01 - Normal: lấy gói PARTNER đang ACTIVE, sắp xếp theo giá tháng tăng dần
        @Test
        void getActivePlanByType_partner_queriesActiveOnly() {
            when(subscriptionPlanRepository.findByPlanTypeAndStatusOrderByPriceMonthlyAsc(
                    PlanType.PARTNER, SubscriptionPlanStatus.ACTIVE))
                    .thenReturn(List.of(plan(1L, SubscriptionPlanStatus.ACTIVE, PlanType.PARTNER)));

            assertEquals(1, subscriptionPlanService.getActivePlanByType(PlanType.PARTNER).size());
            verify(subscriptionPlanRepository).findByPlanTypeAndStatusOrderByPriceMonthlyAsc(
                    PlanType.PARTNER, SubscriptionPlanStatus.ACTIVE);
        }

        // UTCID02 - Normal: lấy gói PREMIUM
        @Test
        void getActivePlanByType_premium_queriesPremiumType() {
            when(subscriptionPlanRepository.findByPlanTypeAndStatusOrderByPriceMonthlyAsc(
                    PlanType.PREMIUM, SubscriptionPlanStatus.ACTIVE)).thenReturn(List.of());

            subscriptionPlanService.getActivePlanByType(PlanType.PREMIUM);

            verify(subscriptionPlanRepository).findByPlanTypeAndStatusOrderByPriceMonthlyAsc(
                    PlanType.PREMIUM, SubscriptionPlanStatus.ACTIVE);
        }

        // UTCID03 - Boundary: không có gói nào đang bán -> danh sách rỗng
        @Test
        void getActivePlanByType_noActivePlans_returnsEmptyList() {
            when(subscriptionPlanRepository.findByPlanTypeAndStatusOrderByPriceMonthlyAsc(any(), any()))
                    .thenReturn(List.of());

            assertTrue(subscriptionPlanService.getActivePlanByType(PlanType.PARTNER).isEmpty());
        }
    }
}
