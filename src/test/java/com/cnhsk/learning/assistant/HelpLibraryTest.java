package com.cnhsk.learning.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;

class HelpLibraryTest {
    private static final String FIXTURES = "assistant-help/valid/";
    private static final String VALID = "classpath*:" + FIXTURES + "*.md";
    private static final String LIMIT_MESSAGE = "Assistant help exceeds 60000 Unicode code points";

    @TempDir
    Path directory;

    @Test
    void FR010_loadsConfiguredClasspathDocumentsInFilenameOrder() throws IOException {
        String expected = fixture("01-intro.md") + "\n\n" + fixture("02-navigation.md");
        new ApplicationContextRunner().withUserConfiguration(HelpLibrary.class)
                .withPropertyValues("cnhsk.assistant.help-location=" + VALID)
                .run(context -> assertThat(context.getBean(HelpLibrary.class).content()).isEqualTo(expected));
    }

    @Test
    void FR010_loadsClasspathDocumentsFromJarInFilenameOrder() throws IOException {
        Path jar = directory.resolve("assistant-help.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("help/"));
            output.closeEntry();
            for (String name : List.of("02-navigation.md", "01-intro.md")) {
                output.putNextEntry(new JarEntry("help/" + name));
                output.write(fixture(name).getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }

        try (var loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            var library = new HelpLibrary(new DefaultResourceLoader(loader), "classpath*:help/*.md");
            assertThat(library.content()).isEqualTo(fixture("01-intro.md") + "\n\n" + fixture("02-navigation.md"));
        }
    }

    @Test
    void FR010_AT10_acceptsExactlySixtyThousandCodePointsAcrossFiles() throws IOException {
        String first = "学".repeat(59_999);
        Files.writeString(directory.resolve("02.md"), "𠀀");
        Files.writeString(directory.resolve("01.md"), first);
        contextForDirectory().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(HelpLibrary.class).content()).isEqualTo(first + "\n\n𠀀");
        });

        Files.writeString(directory.resolve("03.md"), "字");
        contextForDirectory().run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseMessage(LIMIT_MESSAGE));
    }

    @Test
    void FR010_AT10_seventyThousandCodePointsPreventStartupWithoutEchoingContent() throws IOException {
        String marker = "SYNTHETIC_HELP_MARKER";
        Files.writeString(directory.resolve("01.md"), marker + "字".repeat(70_000 - marker.length()));
        contextForDirectory().run(context -> {
            assertThat(context).hasFailed();
            var failure = context.getStartupFailure();
            assertThat(failure).hasRootCauseMessage(LIMIT_MESSAGE);
            var trace = new StringWriter();
            failure.printStackTrace(new PrintWriter(trace));
            assertThat(trace.toString()).doesNotContain(marker);
        });
    }

    @Test
    void FR010_AT10_emptyAndBlankLibrariesStartWithoutAvailableHelp() throws IOException {
        Files.writeString(directory.resolve("ignored.txt"), "Nội dung ngoài mẫu Markdown.");
        contextForDirectory().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(HelpLibrary.class).isEmpty()).isTrue();
        });

        Files.writeString(directory.resolve("blank.md"), " \n\t");
        contextForDirectory().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(HelpLibrary.class).isEmpty()).isTrue();
        });
    }

    private ApplicationContextRunner contextForDirectory() {
        return new ApplicationContextRunner().withUserConfiguration(HelpLibrary.class)
                .withPropertyValues("cnhsk.assistant.help-location=" + directory.toUri() + "*.md");
    }

    private static String fixture(String name) throws IOException {
        return new ClassPathResource(FIXTURES + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
