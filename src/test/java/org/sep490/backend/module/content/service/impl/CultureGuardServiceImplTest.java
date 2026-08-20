package org.sep490.backend.module.content.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.ai.CultureGuardProperties;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.module.content.dto.record.CultureCheckResult;
import org.sep490.backend.module.content.dto.record.CultureVerdict;
import org.sep490.backend.module.content.entity.enumeration.CultureDecision;
import org.sep490.backend.module.content.service.inter.CultureGuardService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test cho bộ lọc chủ đề văn hóa - di sản - lịch sử.
 * Dùng từ điển thật trong classpath để test luôn cả nội dung lexicon.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CultureGuardServiceImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private ChatClient chatClient;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private RedisTemplate<String, Object> redisTemplate;

    private CultureGuardProperties properties;
    private CultureGuardServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new CultureGuardProperties();
        service = new CultureGuardServiceImpl(
                chatClient, properties, redisTemplate, new RedisCircuitBreaker(), new DefaultResourceLoader());
        service.loadLexicon();
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);
    }

    private void stubLlm(double score) {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(CultureVerdict.class))
                .thenReturn(new CultureVerdict(score, List.of("chủ đề"), "lý do từ AI", List.of("gợi ý")));
    }

    // =====================================================================
    // Function: check - tầng luật
    // =====================================================================
    @Nested
    @DisplayName("check - tang luat")
    class RuleTierTest {

        // UTCID01 - Abnormal: trúng từ khóa cấm thì chặn ngay, không tốn tiền gọi LLM
        @Test
        void check_denyTerm_rejectsWithoutCallingLlm() {
            CultureCheckResult result = service.check(CultureGuardService.KIND_TAG, "Đầu tư Bitcoin");

            assertEquals(CultureDecision.REJECT, result.decision());
            assertFalse(result.fromLlm());
            verify(chatClient, never()).prompt();
        }

        // UTCID02 - Normal: tag rõ ràng thuộc chủ đề thì cho qua, không gọi LLM
        @Test
        void check_tagWithAllowTerm_passesWithoutCallingLlm() {
            CultureCheckResult result = service.check(CultureGuardService.KIND_TAG, "Lễ hội truyền thống");

            assertEquals(CultureDecision.PASS, result.decision());
            assertFalse(result.fromLlm());
            verify(chatClient, never()).prompt();
        }

        // UTCID03 - Normal: story cần đủ 3 từ khóa mới được qua thẳng
        @Test
        void check_storyWithEnoughAllowTerms_passesWithoutCallingLlm() {
            CultureCheckResult result = service.check(CultureGuardService.KIND_STORY,
                    "Di tích lịch sử Cổ Loa gắn với tín ngưỡng thờ cúng và lễ hội của làng nghề địa phương");

            assertEquals(CultureDecision.PASS, result.decision());
            assertFalse(result.fromLlm());
            verify(chatClient, never()).prompt();
        }

        // UTCID04 - Normal: từ khóa không dấu vẫn khớp được từ điển
        @Test
        void check_textWithoutDiacritics_stillMatchesLexicon() {
            CultureCheckResult result = service.check(CultureGuardService.KIND_TAG, "am thuc truyen thong");

            assertEquals(CultureDecision.PASS, result.decision());
            verify(chatClient, never()).prompt();
        }

        // UTCID05 - Normal: tắt bộ lọc thì mọi nội dung đều qua
        @Test
        void check_guardDisabled_alwaysPasses() {
            properties.setEnabled(false);

            CultureCheckResult result = service.check(CultureGuardService.KIND_TAG, "Đầu tư Bitcoin");

            assertEquals(CultureDecision.PASS, result.decision());
            verify(chatClient, never()).prompt();
        }
    }

    // =====================================================================
    // Function: check - tầng LLM
    // =====================================================================
    @Nested
    @DisplayName("check - tang LLM")
    class LlmTierTest {

        // UTCID06 - Normal: nội dung mơ hồ, LLM chấm cao thì cho qua
        @Test
        void check_ambiguousText_highScore_passes() {
            stubLlm(0.9);

            CultureCheckResult result = service.check(CultureGuardService.KIND_TAG, "Nghe duc chuong Phuoc Kieu");

            assertEquals(CultureDecision.PASS, result.decision());
            assertTrue(result.fromLlm());
        }

        // UTCID07 - Abnormal: LLM chấm thấp thì từ chối
        @Test
        void check_ambiguousText_lowScore_rejects() {
            stubLlm(0.1);

            CultureCheckResult result = service.check(CultureGuardService.KIND_TAG, "Quan ca phe view dep");

            assertEquals(CultureDecision.REJECT, result.decision());
            assertTrue(result.fromLlm());
        }

        // UTCID08 - Abnormal: điểm vùng xám thì đưa vào diện chờ duyệt
        @Test
        void check_ambiguousText_midScore_needsReview() {
            stubLlm(0.45);

            CultureCheckResult result = service.check(CultureGuardService.KIND_TAG, "Quan ca phe view dep");

            assertEquals(CultureDecision.REVIEW, result.decision());
        }

        // UTCID09 - Abnormal: LLM lỗi thì fail-open sang REVIEW, không được chặn cứng
        @Test
        void check_llmThrows_failsOpenToReview() {
            when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(CultureVerdict.class))
                    .thenThrow(new RuntimeException("groq down"));

            CultureCheckResult result = service.check(CultureGuardService.KIND_TAG, "Quan ca phe view dep");

            assertEquals(CultureDecision.REVIEW, result.decision());
        }

        // UTCID10 - Normal: kết quả đã cache thì không gọi lại LLM
        @Test
        void check_cachedVerdict_doesNotCallLlm() {
            when(redisTemplate.opsForValue().get(anyString()))
                    .thenReturn(new CultureVerdict(0.95, List.of(), "tu cache", List.of()));

            CultureCheckResult result = service.check(CultureGuardService.KIND_TAG, "Quan ca phe view dep");

            assertEquals(CultureDecision.PASS, result.decision());
            assertEquals("tu cache", result.reason());
            verify(chatClient, never()).prompt();
        }
    }

    // =====================================================================
    // Function: checkAndEnforce
    // =====================================================================
    @Nested
    @DisplayName("checkAndEnforce")
    class EnforceTest {

        // UTCID11 - Abnormal: REJECT thì ném lỗi kèm errorCode riêng
        @Test
        void enforce_reject_throwsWithRejectCode() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    service.checkAndEnforce(CultureGuardService.KIND_TAG, "Đầu tư Bitcoin", true));

            assertEquals(CultureGuardServiceImpl.CODE_REJECTED, ex.getErrorCode());
        }

        // UTCID12 - Abnormal: vùng xám mà chưa xác nhận thì chặn và báo mã cần xác nhận
        @Test
        void enforce_reviewWithoutConfirm_throwsReviewRequired() {
            stubLlm(0.45);

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    service.checkAndEnforce(CultureGuardService.KIND_TAG, "Quan ca phe view dep", null));

            assertEquals(CultureGuardServiceImpl.CODE_REVIEW_REQUIRED, ex.getErrorCode());
        }

        // UTCID13 - Normal: vùng xám và đã xác nhận thì cho đi tiếp
        @Test
        void enforce_reviewWithConfirm_returnsReview() {
            stubLlm(0.45);

            CultureCheckResult result =
                    service.checkAndEnforce(CultureGuardService.KIND_TAG, "Quan ca phe view dep", true);

            assertEquals(CultureDecision.REVIEW, result.decision());
        }

        // UTCID14 - Normal: PASS thì đi tiếp bình thường
        @Test
        void enforce_pass_returnsPass() {
            CultureCheckResult result =
                    service.checkAndEnforce(CultureGuardService.KIND_TAG, "Lễ hội truyền thống", null);

            assertEquals(CultureDecision.PASS, result.decision());
        }
    }
}
