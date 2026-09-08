/*
 * Copyright (c) 2021-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.conventions;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("published-consumer")
class PublishedConsumerFunctionalTest {

    private static final List<String> GITHUB_ENVIRONMENT = List.of(
            "GITHUB_ACTIONS",
            "GITHUB_REF_TYPE",
            "GITHUB_REF_NAME",
            "GITHUB_HEAD_REF",
            "GITHUB_RUN_NUMBER"
    );

    @TempDir
    Path projectDir;

    @Test
    void publishedPluginRunsARealMultiProjectBuildWithConfigurationCache() throws IOException {
        String repository = System.getProperty("conventions.testRepository");
        String version = System.getProperty("conventions.testVersion");
        Assumptions.assumeTrue(repository != null && !repository.isBlank());
        Assumptions.assumeTrue(version != null && !version.isBlank());

        Files.writeString(
                projectDir.resolve("settings.gradle"),
                """
                pluginManagement {
                    repositories {
                        maven { url = uri('%s') }
                        maven { url = uri('https://maven.cleanroommc.com') }
                        gradlePluginPortal()
                    }
                }
                plugins {
                    id 'com.cleanroommc.conventions.settings' version '%s'
                }
                rootProject.name = 'published-consumer'
                include 'application'
                """.formatted(
                        repository,
                        version
                )
        );
        Files.writeString(projectDir.resolve("build.gradle"), "");
        Path application = Files.createDirectories(projectDir.resolve("application"));
        Files.writeString(
                application.resolve("build.gradle"),
                """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.conventions'
                }
                """
        );
        Files.writeString(projectDir.resolve("gradle.properties"), "versioning.stage=release\nconventions.license=free\n");
        writeSources(application);
        initRepository();

        run(":application:test", ":application:checkstyleMain", "--configuration-cache");
        BuildResult reused = run(":application:test", ":application:checkstyleMain", "--configuration-cache");

        assertThat(reused.getOutput()).contains("Reusing configuration cache.");
        assertThat(application.resolve("build/classes/java/main/example/Calculator.class")).exists();
        assertThat(application.resolve("build/test-results/test/TEST-example.CalculatorTest.xml")).exists();
    }

    private void writeSources(Path application) throws IOException {
        String header = LicenseMode.FREE.javaHeader(LicenseYears.current()) + "\n\n";
        Path main = application.resolve("src/main/java/example/Calculator.java");
        Files.createDirectories(main.getParent());
        Files.writeString(
                main,
                header + """
                package example;

                public final class Calculator {

                    private Calculator() {}

                    public static int add(int left, int right) {
                        return left + right;
                    }
                }
                """
        );

        Path test = application.resolve("src/test/java/example/CalculatorTest.java");
        Files.createDirectories(test.getParent());
        Files.writeString(
                test,
                header + """
                package example;

                import static org.assertj.core.api.Assertions.assertThat;
                import org.junit.jupiter.api.Test;

                class CalculatorTest {

                    @Test
                    void addsTwoNumbers() {
                        assertThat(Calculator.add(2, 3)).isEqualTo(5);
                    }
                }
                """
        );
    }

    private BuildResult run(String... arguments) {
        Map<String, String> environment = new HashMap<>(System.getenv());
        GITHUB_ENVIRONMENT.forEach(environment::remove);
        return GradleRunner.create().withProjectDir(projectDir.toFile()).withArguments(arguments).withEnvironment(environment).forwardOutput().build();
    }

    private void initRepository() throws IOException {
        git("init", "-b", "master");
        Files.writeString(projectDir.resolve(".git/info/exclude"), "*\n");
        git("-c", "user.email=conventions@example.com", "-c", "user.name=Conventions", "commit", "--allow-empty", "--no-gpg-sign", "-m", "conventions");
    }

    private void git(String... arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).directory(projectDir.toFile()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IOException("git " + String.join(" ", arguments) + " failed: " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

}
