package com.cnhsk.learning.content.importer;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cnhsk.import")
public class ImportProperties {

    @NotNull
    private DataSize maxFileSize = DataSize.ofBytes(CsvReader.MAX_FILE_BYTES);

    @Min(1)
    private int maxRows = CsvReader.MAX_DATA_ROWS;

    @NotNull
    private Duration previewTtl = Duration.ofMinutes(30);

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public Duration getPreviewTtl() {
        return previewTtl;
    }

    public void setPreviewTtl(Duration previewTtl) {
        this.previewTtl = previewTtl;
    }

    @AssertTrue(message = "Import file size must be positive and fit in an in-memory byte array")
    public boolean isFileSizeValid() {
        return maxFileSize != null && maxFileSize.toBytes() > 0 && maxFileSize.toBytes() <= Integer.MAX_VALUE;
    }

    @AssertTrue(message = "Import preview TTL must be positive")
    public boolean isPreviewTtlValid() {
        return previewTtl != null && !previewTtl.isZero() && !previewTtl.isNegative();
    }
}
