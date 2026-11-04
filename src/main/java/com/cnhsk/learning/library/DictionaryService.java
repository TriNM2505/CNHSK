package com.cnhsk.learning.library;

import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.text.TextNormalizer;
import com.cnhsk.learning.library.DictionaryQueryRepository.LexemeRow;
import com.cnhsk.learning.library.DictionaryQueryRepository.Result;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DictionaryService {

    private final DictionaryQueryRepository queries;

    public DictionaryService(DictionaryQueryRepository queries) {
        this.queries = queries;
    }

    public SearchResult search(String type, String q, int page, int size) {
        if (page < 0) throw invalid("page", "Trang phải từ 0 trở lên.");
        if (size < 1 || size > 50) throw invalid("size", "Kích thước trang phải từ 1 đến 50.");
        if (!List.of("HANZI", "PINYIN", "MEANING").contains(type == null ? "" : type)) {
            throw invalid("type", "Kiểu tra phải là HANZI, PINYIN hoặc MEANING.");
        }
        String query = TextNormalizer.normalizeCell("query", q);
        if (query == null || query.isEmpty()) throw invalid("q", "Nhập nội dung cần tra.");
        if (query.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid("q", "Nội dung cần tra chứa ký tự không hợp lệ.");
        }
        int length = query.codePointCount(0, query.length());
        if (length > ("HANZI".equals(type) ? 10 : 50)) {
            return new SearchResult(List.of(), 0, "USE_TRANSLATE");
        }
        Result result;
        switch (type) {
            case "HANZI" -> {
                result = queries.exact(query, page, size);
                if (result.total() == 0 && length > 1) {
                    List<String> characters = query.codePoints()
                            .filter(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)
                            .mapToObj(codePoint -> new String(Character.toChars(codePoint)))
                            .distinct().toList();
                    if (!characters.isEmpty()) result = queries.characters(characters, page, size);
                }
            }
            case "PINYIN" -> result = queries.pinyin(PinyinQuery.parse(query), page, size);
            default -> {
                if (length < 2) throw invalid("q", "Nghĩa Việt cần ít nhất 2 ký tự.");
                result = queries.meaning(TextNormalizer.normalizeMeaning(query), page, size);
            }
        }
        return new SearchResult(result.items().stream().map(DictionaryService::summary).toList(), result.total(), null);
    }

    private static LexemeSummary summary(LexemeRow row) {
        return new LexemeSummary(row.id(), row.kind(), row.text(), row.pinyin(), row.sinoViet(), row.meaningVi(),
                row.hskLevel(), false);
    }

    public static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of("field", field, "message", message))));
    }

    @Schema(name = "LexemeSummary")
    public record LexemeSummary(long id, String kind, String text, String pinyin, String sinoViet, String meaningVi,
                                Integer hskLevel, boolean approximate) {
    }

    @Schema(name = "SearchResult")
    public record SearchResult(List<LexemeSummary> items, long total, String hint) {
    }
}
