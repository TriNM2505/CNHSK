package com.cnhsk.learning.credit.web;

import java.util.LinkedHashMap;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

public class Benefits extends LinkedHashMap<String, Integer> {
    public Benefits() {
        super();
    }

    public Benefits(Map<String, Integer> values) {
        super(values);
    }

    @Schema(name = "Benefits", type = "object", additionalPropertiesSchema = Limit.class,
            description = "Hạn mức mỗi tháng theo tính năng (0–10 000); chỉ 4 khoá PaidFeature")
    public static final class CatalogSchema {
        private CatalogSchema() {
        }
    }

    @Schema(types = {"integer"}, minimum = "0", maximum = "10000")
    public static final class Limit {
        private Limit() {
        }
    }
}
