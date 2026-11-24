package com.cnhsk.learning.content.importer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.cnhsk.learning.knowledge.entity.KnowledgeExamples;
import com.cnhsk.learning.knowledge.entity.KnowledgeSkill;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CSV forms of the v2.0 node attributes. Validation, writing and preview all go through here, so the canonical
 * text is identical everywhere and "unchanged" really means unchanged.
 */
public final class KnowledgeNodeColumns {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CJK = Pattern.compile("[\\p{IsHan}]");
    private static final Pattern ERROR_CODE = Pattern.compile("^E-(NA|CH|TV|NP|NL|VB)-[0-9]{2}$");
    private static final int MAX_EXAMPLES = 10;

    private KnowledgeNodeColumns() { }

    /** "reading | Writing" → "READING|WRITING"; rejects unknown, repeated or empty entries. */
    public static String canonicalSkills(String raw) {
        String[] parts = raw.split("\\|", -1);
        Set<String> skills = new LinkedHashSet<>();
        for (String part : parts) {
            String skill = part.strip().toUpperCase(Locale.ROOT);
            if (skill.isEmpty() || !skills.add(skill)) throw new IllegalArgumentException("empty or repeated skill");
            KnowledgeSkill.valueOf(skill);
        }
        return String.join("|", skills);
    }

    public static String[] skills(String canonical) {
        return canonical.split("\\|");
    }

    /** Order-insensitive comparison: the same set of skills is not a change. */
    public static boolean sameSkills(String[] left, String[] right) {
        return Set.copyOf(Arrays.asList(left)).equals(Set.copyOf(Arrays.asList(right)));
    }

    public static String skillsCell(String[] skills) {
        return skills == null || skills.length == 0 ? null : String.join("|", skills);
    }

    public static List<KnowledgeExamples.Correct> correct(String json) {
        JsonNode array = array(json);
        List<KnowledgeExamples.Correct> result = new ArrayList<>();
        for (JsonNode item : array) {
            String cn = text(item, "cn", true);
            if (!CJK.matcher(cn).find()) throw new IllegalArgumentException("example without Chinese");
            result.add(new KnowledgeExamples.Correct(cn, text(item, "pinyin", false), text(item, "vi", true)));
        }
        return List.copyOf(result);
    }

    public static List<KnowledgeExamples.Wrong> wrong(String json) {
        JsonNode array = array(json);
        List<KnowledgeExamples.Wrong> result = new ArrayList<>();
        for (JsonNode item : array) {
            String cn = text(item, "cn", true);
            if (!CJK.matcher(cn).find()) throw new IllegalArgumentException("example without Chinese");
            String code = text(item, "error_code", false);
            if (code != null && !ERROR_CODE.matcher(code).matches()) throw new IllegalArgumentException("bad error code");
            result.add(new KnowledgeExamples.Wrong(cn, code, text(item, "note", false)));
        }
        return List.copyOf(result);
    }

    public static String correctCell(List<KnowledgeExamples.Correct> examples) {
        if (examples.isEmpty()) return null;
        ArrayNode array = JSON.createArrayNode();
        for (var example : examples) {
            ObjectNode node = array.addObject().put("cn", example.cn());
            if (example.pinyin() != null) node.put("pinyin", example.pinyin());
            node.put("vi", example.vi());
        }
        return array.toString();
    }

    public static String wrongCell(List<KnowledgeExamples.Wrong> examples) {
        if (examples.isEmpty()) return null;
        ArrayNode array = JSON.createArrayNode();
        for (var example : examples) {
            ObjectNode node = array.addObject().put("cn", example.cn());
            if (example.errorCode() != null) node.put("error_code", example.errorCode());
            if (example.note() != null) node.put("note", example.note());
        }
        return array.toString();
    }

    private static JsonNode array(String json) {
        try {
            JsonNode node = JSON.readTree(json);
            if (node == null || !node.isArray() || node.isEmpty() || node.size() > MAX_EXAMPLES) {
                throw new IllegalArgumentException("expected a JSON array of 1-" + MAX_EXAMPLES + " examples");
            }
            return node;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("invalid JSON");
        }
    }

    private static String text(JsonNode item, String field, boolean required) {
        if (!item.isObject()) throw new IllegalArgumentException("example must be an object");
        JsonNode value = item.get(field);
        if (value == null || value.isNull()) {
            if (required) throw new IllegalArgumentException("missing " + field);
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 500) {
            throw new IllegalArgumentException("bad " + field);
        }
        return value.textValue().strip();
    }
}
