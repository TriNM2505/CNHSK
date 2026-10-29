package com.cnhsk.learning.content.importer.template;

public record ColumnSpec(
        String name,
        boolean required,
        Integer maxLength,
        String description,
        String example) {
}
