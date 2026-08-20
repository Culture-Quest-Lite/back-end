package org.sep490.backend.module.content.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.utils.TextUtils;
import org.sep490.backend.config.ai.CultureGuardProperties;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.module.content.dto.record.CultureCheckResult;
import org.sep490.backend.module.content.dto.record.CultureVerdict;
import org.sep490.backend.module.content.entity.enumeration.CultureDecision;
import org.sep490.backend.module.content.service.inter.CultureGuardService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CultureGuardServiceImpl implements CultureGuardService {

    public static final String CODE_REJECTED = "CULTURE_REJECTED";
    public static final String CODE_REVIEW_REQUIRED = "CULTURE_REVIEW_REQUIRED";

    static final String REASON_DENY =
            "Nội dung chứa chủ đề không phù hợp với định hướng văn hóa - di sản - lịch sử Việt Nam";
    static final String REASON_ALLOW =
            "Nội dung bám các chủ đề văn hóa - di sản - lịch sử Việt Nam";
    static final String REASON_LLM_DOWN =
            "Chưa kiểm duyệt tự động được, cần người kiểm duyệt xác nhận thủ công";

    final ChatClient chatClient;
    final CultureGuardProperties properties;
    final RedisTemplate<String, Object> redisTemplate;
    final RedisCircuitBreaker circuitBreaker;
    final ResourceLoader resourceLoader;

    Set<String> allowTerms = Set.of();
    Set<String> denyTerms = Set.of();

    @PostConstruct
    void loadLexicon() {
        try (InputStream in = resourceLoader.getResource(properties.getLexiconPath()).getInputStream()) {
            Map<String, List<String>> raw = new Yaml().load(in);
            allowTerms = normalizeTerms(raw.get("allow"));
            denyTerms = normalizeTerms(raw.get("deny"));
            log.info("[CultureGuard] Nạp từ điển: {} allow, {} deny", allowTerms.size(), denyTerms.size());
        } catch (Exception e) {
            log.error("[CultureGuard] Không nạp được từ điển {}: {}", properties.getLexiconPath(), e.getMessage());
        }
    }

    @Override
    public CultureCheckResult check(String kind, String text) {
        if (!properties.isEnabled()) {
            return CultureCheckResult.rule(CultureDecision.PASS, 1d, "Bộ lọc văn hóa đang tắt", List.of());
        }
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return CultureCheckResult.rule(CultureDecision.PASS, 1d, "Không có nội dung để kiểm duyệt", List.of());
        }

        Set<String> denyHits = findHits(normalized, denyTerms);
        if (!denyHits.isEmpty()) {
            return CultureCheckResult.rule(CultureDecision.REJECT, 0d, REASON_DENY, List.copyOf(denyHits));
        }

        Set<String> allowHits = findHits(normalized, allowTerms);
        int minHits = KIND_STORY.equals(kind) ? properties.getStoryMinAllowHits() : properties.getTagMinAllowHits();
        if (allowHits.size() >= minHits) {
            return CultureCheckResult.rule(CultureDecision.PASS, 1d, REASON_ALLOW, List.copyOf(allowHits));
        }

        CultureVerdict verdict = classifyWithCache(kind, normalized, truncate(text, kind));
        if (verdict == null) {
            return new CultureCheckResult(
                    CultureDecision.REVIEW,
                    new CultureVerdict(properties.getRejectThreshold(), List.of(), REASON_LLM_DOWN, List.of()),
                    true);
        }
        return new CultureCheckResult(toDecision(verdict.safeScore()), verdict, true);
    }

    @Override
    public CultureCheckResult checkAndEnforce(String kind, String text, Boolean confirmCultural) {
        CultureCheckResult result = check(kind, text);
        if (result.decision() == CultureDecision.REJECT) {
            throw BusinessException.withCode(HttpStatus.BAD_REQUEST, CODE_REJECTED,
                    "Nội dung không phù hợp với chủ đề văn hóa - di sản - lịch sử Việt Nam. {}{}",
                    result.reason(), formatSuggestions(result.verdict()));
        }
        if (result.decision() == CultureDecision.REVIEW && !Boolean.TRUE.equals(confirmCultural)) {
            throw BusinessException.withCode(HttpStatus.BAD_REQUEST, CODE_REVIEW_REQUIRED,
                    "Nội dung chưa rõ có thuộc chủ đề văn hóa - di sản - lịch sử Việt Nam hay không. {}{}"
                            + " Nếu chắc chắn đúng chủ đề, gửi lại với confirmCultural=true để chuyển kiểm duyệt viên duyệt.",
                    result.reason(), formatSuggestions(result.verdict()));
        }
        return result;
    }

    static String formatSuggestions(CultureVerdict verdict) {
        List<String> suggestions = verdict.safeSuggestions();
        return suggestions.isEmpty() ? "" : " Gợi ý: " + String.join("; ", suggestions) + ".";
    }

    CultureDecision toDecision(double score) {
        if (score >= properties.getPassThreshold()) {
            return CultureDecision.PASS;
        }
        if (score >= properties.getRejectThreshold()) {
            return CultureDecision.REVIEW;
        }
        return CultureDecision.REJECT;
    }

    CultureVerdict classifyWithCache(String kind, String normalized, String prompt) {
        String cacheKey = buildCacheKey(kind, normalized);

        Object cached = circuitBreaker.read("cultureGuard.get",
                () -> redisTemplate.opsForValue().get(cacheKey), null);
        if (cached instanceof CultureVerdict hit) {
            return hit;
        }

        CultureVerdict verdict = callLlm(kind, prompt);
        if (verdict != null) {
            circuitBreaker.write("cultureGuard.set",
                    () -> redisTemplate.opsForValue().set(cacheKey, verdict, CacheNames.TTL_AI));
        }
        return verdict;
    }

    String buildCacheKey(String kind, String normalized) {
        String raw = kind + "|" + normalized;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return CacheNames.KEY_CULTURE_CHECK + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return CacheNames.KEY_CULTURE_CHECK + raw.hashCode();
        }
    }

    CultureVerdict callLlm(String kind, String prompt) {
        String system = """
                Bạn là bộ kiểm duyệt nội dung của một ứng dụng du lịch chuyên về VĂN HÓA - DI SẢN - LỊCH SỬ VIỆT NAM.
                Nhiệm vụ: chấm điểm mức độ liên quan của nội dung với các lĩnh vực sau:
                di sản văn hóa, di tích lịch sử, lịch sử Việt Nam, tín ngưỡng - tôn giáo truyền thống,
                lễ hội, phong tục tập quán, làng nghề thủ công, ẩm thực truyền thống,
                nghệ thuật dân gian, kiến trúc cổ, trang phục truyền thống, văn hóa các dân tộc Việt Nam.

                QUY TẮC BẮT BUỘC:
                - score là số thực trong khoảng 0..1. 1 = hoàn toàn thuộc các lĩnh vực trên, 0 = không liên quan gì.
                - Nội dung thương mại thuần túy, tài chính, cờ bạc, người lớn, chính trị kích động: score < 0.2.
                - Địa điểm/hoạt động du lịch hiện đại không gắn yếu tố văn hóa - lịch sử: score 0.3 - 0.5.
                - themes: liệt kê ngắn gọn các lĩnh vực nhận diện được, bằng tiếng Việt.
                - reason: MỘT câu tiếng Việt, giải thích vì sao chấm điểm như vậy.
                - suggestions: 1-3 gợi ý tiếng Việt giúp chỉnh nội dung bám chủ đề hơn. Nếu score cao thì để mảng rỗng.

                VÍ DỤ:
                - "Lễ hội Gióng đền Sóc" -> score 0.95, themes ["lễ hội", "tín ngưỡng"]
                - "Nghề dệt thổ cẩm của người Thái ở Mai Châu" -> score 0.92, themes ["làng nghề", "văn hóa dân tộc"]
                - "Quán cà phê view đẹp check-in" -> score 0.15, themes []
                - "Đầu tư Bitcoin sinh lời" -> score 0.0, themes []
                """;

        String user = ("TAG".equals(kind) ? "Tên thẻ chủ đề cần kiểm duyệt:\n" : "Nội dung câu chuyện cần kiểm duyệt:\n")
                + prompt;

        try {
            return chatClient.prompt()
                    .system(system)
                    .user(user)
                    .call()
                    .entity(CultureVerdict.class);
        } catch (Exception e) {
            log.warn("[CultureGuard] Gọi LLM lỗi: {}", e.getMessage());
            return null;
        }
    }

    String truncate(String text, String kind) {
        if (text == null) {
            return "";
        }
        int max = KIND_STORY.equals(kind) ? properties.getStoryMaxChars() : 200;
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return TextUtils.removeDiacritics(text)
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    static Set<String> normalizeTerms(List<String> terms) {
        if (terms == null) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (String term : terms) {
            String normalized = normalize(term);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    static Set<String> findHits(String normalizedText, Set<String> terms) {
        String padded = " " + normalizedText + " ";
        Set<String> hits = new LinkedHashSet<>();
        for (String term : terms) {
            if (padded.contains(" " + term + " ")) {
                hits.add(term);
            }
        }
        return hits;
    }
}
