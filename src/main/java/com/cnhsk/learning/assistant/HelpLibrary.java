package com.cnhsk.learning.assistant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.StringJoiner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
public class HelpLibrary {
    private static final int MAX_CODE_POINTS = 60_000;
    private final String content;

    public HelpLibrary(ResourceLoader loader,
            @Value("${cnhsk.assistant.help-location:classpath*:help/*.md}") String location) {
        try {
            var resolver = new PathMatchingResourcePatternResolver(loader);
            // The library reads once at startup and does not need cached JAR handles.
            resolver.setUseCaches(false);
            Resource[] files = resolver.getResources(location);
            Arrays.sort(files, Comparator.comparing(Resource::getFilename));
            var joined = new StringJoiner("\n\n");
            int total = 0;
            for (Resource file : files) {
                String text = file.getContentAsString(StandardCharsets.UTF_8);
                int length = text.codePointCount(0, text.length());
                if (length > MAX_CODE_POINTS - total) {
                    throw new IllegalStateException("Assistant help exceeds 60000 Unicode code points");
                }
                total += length;
                joined.add(text);
            }
            content = joined.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load assistant help");
        }
    }

    public String content() {
        return content;
    }

    public boolean isEmpty() {
        return content.isBlank();
    }
}
