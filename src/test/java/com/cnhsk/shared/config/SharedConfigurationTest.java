package com.cnhsk.shared.config;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import static org.assertj.core.api.Assertions.assertThat;

class SharedConfigurationTest {

    private final SharedConfiguration configuration = new SharedConfiguration();

    @Test
    void providesUtcClock() {
        Clock clock = configuration.clock();

        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(clock.instant()).isNotNull();
    }

    @Test
    void configuresJacksonToUseUtc() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        Jackson2ObjectMapperBuilderCustomizer customizer = configuration.jacksonUtcCustomizer();
        customizer.customize(builder);
        builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ObjectMapper objectMapper = builder.build();

        assertThat(objectMapper.getSerializationConfig().getTimeZone().getID()).isEqualTo("UTC");
        assertThat(objectMapper.writeValueAsString(new Date(0))).contains("1970-01-01T00:00:00.000+00:00");
    }
}
