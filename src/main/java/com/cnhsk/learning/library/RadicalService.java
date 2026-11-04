package com.cnhsk.learning.library;

import java.util.List;

import com.cnhsk.learning.content.text.TextNormalizer;
import com.cnhsk.learning.library.DictionaryService.LexemeSummary;
import com.cnhsk.learning.library.DictionaryService.SearchResult;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RadicalService {

    private final DictionaryQueryRepository queries;

    public RadicalService(DictionaryQueryRepository queries) {
        this.queries = queries;
    }

    public List<RadicalCount> radicals() {
        return queries.radicals();
    }

    public SearchResult search(String radical, Integer strokes, int page, int size) {
        if (page < 0) throw DictionaryService.invalid("page", "Trang phải từ 0 trở lên.");
        if (size < 1 || size > 50) throw DictionaryService.invalid("size", "Kích thước trang phải từ 1 đến 50.");
        String normalized = TextNormalizer.normalizeCell("radical", radical);
        if (normalized != null && normalized.isEmpty()) normalized = null;
        if (normalized != null && (normalized.codePointCount(0, normalized.length()) > 10
                || normalized.codePoints().anyMatch(Character::isISOControl))) {
            throw DictionaryService.invalid("radical", "Bộ thủ không hợp lệ hoặc dài quá 10 ký tự.");
        }
        if (strokes != null && (strokes < 1 || strokes > 64)) {
            throw DictionaryService.invalid("strokes", "Số nét phải từ 1 đến 64.");
        }
        if (normalized == null && strokes == null) {
            throw DictionaryService.invalid("radical", "Cần bộ thủ hoặc số nét để tra.");
        }
        var result = queries.byRadical(normalized, strokes, false, page, size);
        boolean approximate = result.total() == 0 && strokes != null;
        if (approximate) result = queries.byRadical(normalized, strokes, true, page, size);
        boolean fallback = approximate;
        return new SearchResult(result.items().stream().map(row -> new LexemeSummary(row.id(), row.kind(),
                row.text(), row.pinyin(), row.sinoViet(), row.meaningVi(), row.hskLevel(), fallback)).toList(),
                result.total(), null);
    }

    @Schema(name = "RadicalCount")
    public record RadicalCount(String radical, long charCount) {
    }
}
