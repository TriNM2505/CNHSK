package com.cnhsk.learning.library;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.EntitlementApi;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.library.web.TranslateRequest;
import com.cnhsk.learning.library.web.TranslateResult;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TranslationService {
    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    private static final Duration CACHE_TTL = Duration.ofDays(7);
    public static final String UNAVAILABLE_MESSAGE = "Dịch tạm thời lỗi — lượt đã được hoàn.";
    private final EntitlementApi entitlement;
    private final ClaudeTranslator translator;
    private final Segmenter segmenter;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate billing;

    public TranslationService(EntitlementApi entitlement, ClaudeTranslator translator, Segmenter segmenter,
            StringRedisTemplate redis, ObjectMapper mapper, Clock clock, PlatformTransactionManager transactions) {
        this.entitlement = entitlement;
        this.translator = translator;
        this.segmenter = segmenter;
        this.redis = redis;
        this.mapper = mapper;
        this.clock = clock;
        billing = new TransactionTemplate(transactions);
        billing.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public TranslateResult translate(long userId, TranslateRequest request) {
        String digest = digest(request);
        String cacheKey = "translate:" + digest;
        String cached = read(cacheKey);
        if (cached != null) return result(request, cached, true, null);
        String idemKey = digest + ":" + clock.instant().getEpochSecond() / 60;
        Consumption receipt = billing.execute(status -> entitlement.consume(userId, PaidFeature.TRANSLATE, idemKey));
        try {
            String translated = translator.translate(request);
            TranslateResult response = result(request, translated, false, receipt.source());
            write(cacheKey, translated);
            return response;
        } catch (RuntimeException exception) {
            log.warn("Translation failed type={}", exception.getClass().getSimpleName());
            try { billing.executeWithoutResult(status -> entitlement.refund(userId, receipt)); }
            catch (RuntimeException refundError) { log.warn("Translation refund failed type={}", refundError.getClass().getSimpleName()); }
            throw new ApiException(ErrorCode.TRANSLATE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE, Map.of());
        }
    }

    private TranslateResult result(TranslateRequest request, String translated, boolean cached, Consumption.Source source) {
        try {
            Segmenter.Result parts = segmenter.segment(request.from() == TranslateRequest.Language.ZH ? request.text() : translated);
            List<TranslateResult.Segment> segments = parts.segments().stream()
                    .map(part -> new TranslateResult.Segment(part.text(), part.pinyin(), part.lexemeId())).toList();
            return new TranslateResult(translated, parts.pinyin(), segments, cached, source);
        } catch (RuntimeException exception) {
            log.warn("Translation segmentation failed type={}", exception.getClass().getSimpleName());
            return new TranslateResult(translated, null, List.of(), cached, source);
        }
    }

    private String read(String key) {
        try {
            String value = redis.opsForValue().get(key);
            if (value == null) return null;
            JsonNode root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(value);
            if (root != null && root.isObject() && root.path("translation").isTextual()
                    && !root.path("translation").textValue().isBlank()) return root.path("translation").textValue();
            log.warn("Translation cache value unavailable");
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Translation cache read failed type={}", exception.getClass().getSimpleName());
        }
        return null;
    }

    private void write(String key, String translation) {
        try { redis.opsForValue().set(key, mapper.writeValueAsString(Map.of("translation", translation)), CACHE_TTL); }
        catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Translation cache write failed type={}", exception.getClass().getSimpleName());
        }
    }

    static String digest(TranslateRequest request) {
        try {
            byte[] value = (request.from() + "|" + request.to() + "|" + request.text()).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
