package com.cnhsk.learning.library;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.library.LexemeDetailQueryRepository.ReferenceRow;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LexemeDetailService {

    private final LexemeDetailQueryRepository queries;
    private final ObjectMapper mapper;

    public LexemeDetailService(LexemeDetailQueryRepository queries, ObjectMapper mapper) {
        this.queries = queries;
        this.mapper = mapper;
    }

    public LexemeDetail detail(String id) {
        long lexemeId;
        try {
            if (id == null || !id.matches("[0-9]+")) throw new NumberFormatException();
            lexemeId = Long.parseLong(id);
            if (lexemeId < 1) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw notFound();
        }
        var row = queries.find(lexemeId).orElseThrow(LexemeDetailService::notFound);
        List<ReferenceRow> references = queries.references(lexemeId);
        // The existing 006 canvas loads actual stroke data by character, including when strokeCount is absent.
        Map<String, String> strokeData = "CHAR".equals(row.kind()) ? Map.of("source", "hanzi-writer-data") : null;
        return new LexemeDetail(row.id(), row.kind(), row.text(), row.pinyin(), row.sinoViet(), row.meaningVi(),
                row.hskLevel(), row.audioUrl(), row.radical(), row.strokeCount(), strokeData, examples(row.examples()),
                row.structure(), bucket(references, "components"), bucket(references, "containedIn"), bucket(references, "related"));
    }

    private List<LexemeExample> examples(String json) {
        if (json == null) return List.of();
        try {
            JsonNode values = mapper.readTree(json);
            if (!values.isArray()) return List.of();
            List<LexemeExample> result = new ArrayList<>();
            for (JsonNode value : values) {
                if (value.isObject()) result.add(new LexemeExample(text(value, "cn"), text(value, "pinyin"), text(value, "vi")));
            }
            return List.copyOf(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored dictionary examples", exception);
        }
    }

    private static String text(JsonNode value, String field) {
        return value.path(field).isTextual() ? value.path(field).textValue() : null;
    }

    private static List<LexemeRef> bucket(List<ReferenceRow> references, String name) {
        return references.stream().filter(row -> name.equals(row.bucket())).map(ReferenceRow::lexeme).toList();
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.LEXEME_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy mục từ.", Map.of());
    }

    @Schema(name = "LexemeDetail")
    public record LexemeDetail(long id, String kind, String text, String pinyin, String sinoViet, String meaningVi,
                               Integer hskLevel, String audioUrl, String radical, Integer strokeCount,
                               Map<String, String> strokeData, List<LexemeExample> examples, String structure,
                               List<LexemeRef> components, List<LexemeRef> containedIn, List<LexemeRef> related) {
    }

    @Schema(name = "LexemeRef")
    public record LexemeRef(long id, String text, String pinyin, String meaningVi) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LexemeExample(String cn, String pinyin, String vi) {
    }
}
