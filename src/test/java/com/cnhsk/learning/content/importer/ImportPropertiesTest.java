package com.cnhsk.learning.content.importer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportPropertiesTest {
    @Test
    void FR003_configuredFileLimitIsEnforcedByReader() {
        ImportProperties properties = new ImportProperties();
        properties.setMaxFileSize(DataSize.ofBytes(8));
        assertThatThrownBy(() -> new CsvReader(properties).read(ImportDataType.TOPIC, "file.csv", new byte[9]))
                .isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE));
    }

    @Test
    void FR007_configuredRowLimitIsEnforcedByReader() {
        ImportProperties properties = new ImportProperties();
        properties.setMaxRows(1);
        assertThatThrownBy(() -> new CsvReader(properties).read(ImportDataType.TOPIC, "file.csv",
                "code,name,hsk_level\nAA,Tên,1\nBB,Tên,1\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.TOO_MANY_ROWS));
    }

    @Test
    void FR012_limitsAndPreviewLifetimeMustBePositiveAndFitInMemory() {
        ImportProperties properties = new ImportProperties();
        assertThat(properties.getPreviewTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(2));
        assertThat(properties.getMaxRows()).isEqualTo(5000);
        assertThat(properties.isPreviewTtlValid()).isTrue();
        assertThat(properties.isFileSizeValid()).isTrue();
        for (Duration duration : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1), null}) {
            properties.setPreviewTtl(duration);
            assertThat(properties.isPreviewTtlValid()).isFalse();
        }
        for (DataSize size : new DataSize[]{DataSize.ofBytes(0), DataSize.ofBytes(-1), DataSize.ofGigabytes(2), null}) {
            properties.setMaxFileSize(size);
            assertThat(properties.isFileSizeValid()).isFalse();
        }
    }
}
