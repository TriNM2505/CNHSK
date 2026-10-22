package com.cnhsk.shared.config;

import java.time.Clock;
import java.util.TimeZone;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CnhskProperties.class)
public class SharedConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonUtcCustomizer() {
        return builder -> builder.timeZone(TimeZone.getTimeZone("UTC"));
    }
}
