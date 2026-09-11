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
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ConventionsPluginFunctionalTest {

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
    void formatJRunsFromThePackedStyleWithoutAnyFileOnDisk() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.style'", "");
        Path source = projectDir.resolve("src/main/java/example/Example.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package example;\n\npublic class Example {\n            void run() {\n            }\n}\n");

        run("formatJavaApply");

        assertThat(Files.readString(source)).contains("\n    void run() {");
        assertThat(projectDir.resolve("formatj.toml")).doesNotExist();
    }

    @Test
    void aFullBuildLeavesTheProjectDirectoryAlone() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions'", "");

        run("assemble");

        assertThat(projectDir.resolve("checkstyle.xml")).doesNotExist();
        assertThat(projectDir.resolve("formatj.toml")).doesNotExist();
        assertThat(projectDir.resolve("cliff.toml")).doesNotExist();
        assertThat(projectDir.resolve("LICENSE")).doesNotExist();
        assertThat(projectDir.resolve("HEADER")).doesNotExist();
        assertThat(projectDir.resolve(".editorconfig")).doesNotExist();
        assertThat(projectDir.resolve(".gitattributes")).doesNotExist();
        assertThat(projectDir.resolve(".gitignore")).doesNotExist();
    }

    @Test
    void aggregatePluginAppliesTokenEnvoy() throws IOException {
        project(
                "id 'java'\n    id 'com.cleanroommc.conventions'",
                """
                tokenEnvoy {
                    set 'VERSION', '1.2.3'
                }
                """
        );
        Path resource = projectDir.resolve("src/main/resources/version.txt");
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, "version=@{VERSION}\n");

        run("processResources");

        assertThat(Files.readString(projectDir.resolve("build/resources/main/version.txt"))).isEqualTo("version=1.2.3\n");
    }

    @Test
    void tokenEnvoyReplacesTokensInCompiledClasses() throws IOException {
        project(
                "id 'java'\n    id 'com.cleanroommc.conventions'",
                """
                tokenEnvoy {
                    set 'VERSION', '1.2.3'
                }
                """
        );
        javaFile(
                "src/main/java/example/Example.java",
                "package example;\n\npublic class Example {\n\n    public static final String VERSION = \"@{VERSION}\";\n\n}\n"
        );

        run("compileJava");

        assertThat(Files.readAllBytes(projectDir.resolve("build/classes/java/main/example/Example.class")))
                .asString(StandardCharsets.ISO_8859_1)
                .contains("1.2.3")
                .doesNotContain("@{VERSION}");
    }

    @Test
    void extractConventionsWritesPackedFiles() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions'", "");
        Files.writeString(projectDir.resolve(".gitignore"), "# >>> cleanroom-conventions\nold/\n# <<< cleanroom-conventions\nmine.iml\n");

        run("extractConventions");

        assertThat(projectDir.resolve("checkstyle.xml")).exists();
        assertThat(projectDir.resolve("formatj.toml")).exists();
        assertThat(projectDir.resolve("cliff.toml")).exists();
        assertThat(Files.readString(projectDir.resolve("LICENSE"))).contains("CleanroomMC License");
        assertThat(Files.readString(projectDir.resolve("HEADER"))).contains("CleanroomMC License Version 1.0");
        assertThat(projectDir.resolve(".editorconfig")).exists();
        assertThat(projectDir.resolve(".gitattributes")).exists();
        String gitignore = Files.readString(projectDir.resolve(".gitignore"));
        assertThat(gitignore).contains("# >>> cleanroom-conventions");
        assertThat(gitignore).contains("mine.iml");
        assertThat(gitignore).contains(".gradle/");
        assertThat(gitignore).doesNotContain("old/");
        assertThat(run("assemble").getOutput()).doesNotContain("extractConventions");
    }

    @ParameterizedTest
    @EnumSource(LicenseMode.class)
    void extractConventionsWritesTheSelectedLicense(LicenseMode license) throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions'", "");
        property("conventions.license = " + license.propertyValue());

        run("extractConventions");

        LicenseYears years = LicenseYears.current();
        assertThat(Files.readString(projectDir.resolve("LICENSE"))).isEqualTo(license.licenseText(years));
        assertThat(Files.readString(projectDir.resolve("HEADER"))).isEqualTo(license.headerText(years));
        assertThat(Files.readString(projectDir.resolve("LICENSE"))).doesNotContain("@YEAR@");
        assertThat(Files.readString(projectDir.resolve("HEADER"))).doesNotContain("@YEAR@");
        assertThat(Files.readString(projectDir.resolve("checkstyle.xml"))).doesNotContain("@LICENSE_HEADER@");
    }

    @Test
    void beginFromSetsTheCopyrightYearRange() throws IOException {
        int current = Year.now().getValue();
        project("id 'java'\n    id 'com.cleanroommc.conventions'", "conventions { beginFrom = 2001 }");

        run("extractConventions");

        assertThat(Files.readString(projectDir.resolve("HEADER"))).contains("Copyright (c) 2001-" + current + " CleanroomMC contributors");
        assertThat(Files.readString(projectDir.resolve("LICENSE"))).contains("Copyright © 2001-" + current + " CleanroomMC contributors");
    }

    @Test
    void extractionPreservesThePreviousYearAsTheBeginning() throws IOException {
        int current = Year.now().getValue();
        int previous = current - 1;
        project("id 'java'\n    id 'com.cleanroommc.conventions'", "");
        Files.writeString(projectDir.resolve("HEADER"), LicenseMode.VISIBLE.headerText(LicenseYears.of(previous, previous)));

        run("extractConventions");

        assertThat(Files.readString(projectDir.resolve("HEADER"))).contains("Copyright (c) " + previous + "-" + current + " CleanroomMC contributors");
        assertThat(Files.readString(projectDir.resolve("LICENSE"))).contains("Copyright © " + previous + "-" + current + " CleanroomMC contributors");
    }

    @Test
    void extractionFindsTheBeginningYearInAParentDirectory() throws IOException {
        int current = Year.now().getValue();
        int begin = current - 2;
        Path parent = projectDir;
        projectDir = Files.createDirectory(parent.resolve("consumer"));
        project("id 'java'\n    id 'com.cleanroommc.conventions'", "");
        Files.writeString(parent.resolve("HEADER"), LicenseMode.VISIBLE.headerText(LicenseYears.of(begin, current - 1)));

        run("extractConventions");

        assertThat(Files.readString(projectDir.resolve("HEADER"))).contains("Copyright (c) " + begin + "-" + current + " CleanroomMC contributors");
    }

    @Test
    void editingTheHeaderYearOutlivesTheConfigurationCache() throws IOException {
        int current = Year.now().getValue();
        int begin = current - 3;
        project("id 'java'\n    id 'com.cleanroommc.conventions'", "");
        run("--configuration-cache", "extractConventions");
        Files.writeString(projectDir.resolve("HEADER"), LicenseMode.VISIBLE.headerText(LicenseYears.of(begin, begin)));

        BuildResult second = run("--configuration-cache", "extractConventions");

        assertThat(second.getOutput()).contains("Reusing configuration cache.");
        assertThat(Files.readString(projectDir.resolve("HEADER"))).contains("Copyright (c) " + begin + "-" + current + " CleanroomMC contributors");
    }

    @Test
    void beginFromRejectsAFutureYear() throws IOException {
        int future = Year.now().getValue() + 1;
        project("id 'java'\n    id 'com.cleanroommc.conventions'", "conventions { beginFrom = " + future + " }");
        assertThat(runAndFail("extractConventions").getOutput()).contains("beginFrom must not be later than the current year");
    }

    @Test
    void groupDefaultsToCleanroomMc() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.base'", printGroup());
        assertThat(run("printGroup").getOutput()).contains("group=com.cleanroommc");
    }

    @Test
    void groupCanBeOverriddenAfterThePlugin() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.base'", "group = 'zone.rong'\n\n" + printGroup());
        assertThat(run("printGroup").getOutput()).contains("group=zone.rong");
    }

    @Test
    void annotationLibrariesAreCompileOnly() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.annotations'", printCompileOnly());
        String output = run("printCompileOnly").getOutput();
        assertThat(output).contains("org.jspecify:jspecify:1.0.0");
        assertThat(output).contains("org.jetbrains:annotations:26.1.0");
        assertThat(output).contains("com.cleanroommc:anone:1.0.0");
    }

    @Test
    void anoneCanBeDisabledThroughTheExtension() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.annotations'", "conventions { anoneVersion = '' }\n\n" + printCompileOnly());
        assertThat(run("printCompileOnly").getOutput()).doesNotContain("com.cleanroommc:anone");
    }

    @Test
    void annotationVersionsCanBeConfiguredThroughTheExtension() throws IOException {
        project(
                "id 'java'\n    id 'com.cleanroommc.conventions.annotations'",
                "conventions {\n    jspecifyVersion = '0.3.0'\n    jetbrainsAnnotationsVersion = '26.0.2'\n    anoneVersion = '0.9.0'\n}\n\n" + printCompileOnly()
        );
        String output = run("printCompileOnly").getOutput();
        assertThat(output).contains("org.jspecify:jspecify:0.3.0");
        assertThat(output).contains("org.jetbrains:annotations:26.0.2");
        assertThat(output).contains("com.cleanroommc:anone:0.9.0");
    }

    @Test
    void baseConventionsDoNotAddAnnotationLibraries() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.base'", printCompileOnly());
        assertThat(run("printCompileOnly").getOutput()).doesNotContain("org.jspecify:jspecify");
    }

    @Test
    void jarManifestMatchesThePomIdentity() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.base'", printManifest());
        String output = run("jar").getOutput();
        assertThat(output).contains("title=conventions-under-test");
        assertThat(output).contains("vendor=CleanroomMC");
        assertThat(output).contains("vendorId=com.cleanroommc");
        assertThat(output).contains("specVendor=CleanroomMC");
    }

    @Test
    void signingStaysOffWithoutKeys() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.publishing'", "");
        String output = run("tasks", "--all").getOutput();
        assertThat(output).doesNotContain("signMavenPublication");
    }

    @Test
    void signingRegistersWhenKeysArePresent() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.publishing'", "");
        property("signingKey = not-a-real-key");
        property("signingPassword = not-a-real-password");
        assertThat(run("tasks", "--all").getOutput()).contains("signMavenPublication");
    }

    @Test
    void settingsPluginKeepsConventionRepositoriesWhenTheProjectAddsMore() throws IOException {
        settingsProject(
                """
                plugins {
                    id 'com.cleanroommc.conventions.settings'
                }
                rootProject.name = 'conventions-under-test'
                """,
                """
                plugins { id 'java' }
                repositories {
                    maven {
                        name = 'Extra'
                        url = 'https://example.invalid'
                    }
                }
                tasks.register('printRepos') {
                    def names = repositories.collect { it.name }.join(',')
                    doLast { println "repos=[$names]" }
                }
                """
        );
        String output = run("printRepos").getOutput();
        assertThat(output).contains("MavenRepo");
        assertThat(output).contains("Cleanroom");
        assertThat(output).contains("Extra");
        assertThat(output.indexOf("MavenRepo")).isLessThan(output.indexOf("Extra"));
        assertThat(output.indexOf("Cleanroom")).isLessThan(output.indexOf("Extra"));
    }

    @Test
    void settingsPluginDoesNotApplyFoojayByDefault() throws IOException {
        settingsProject(printSettings("foojay"), "plugins { id 'java' }\n");
        assertThat(run("help").getOutput()).contains("foojay=false");
    }

    @Test
    void settingsPluginAppliesFoojayWhenProvisionJavaIsOn() throws IOException {
        settingsProject(printSettings("foojay"), "plugins { id 'java' }\n");
        Files.writeString(projectDir.resolve("gradle.properties"), "conventions.provisionJava = true\n");
        assertThat(run("help").getOutput()).contains("foojay=true");
    }

    @Test
    void checkstyleWarnsAboutImportedForeignNullness() throws IOException {
        project(
                "id 'java'\n    id 'com.cleanroommc.conventions'",
                """
                dependencies {
                    compileOnly 'com.google.code.findbugs:jsr305:3.0.2'
                }
                """
        );
        Path source = projectDir.resolve("src/main/java/example/Example.java");
        Files.createDirectories(source.getParent());
        Files.writeString(
                source,
                javaSource(
                        """
                        package example;

                        %s

                        public class Example {

                            @Nullable
                            public String name() {
                                return null;
                            }

                        }
                        """.formatted(
                                "import javax.annotation.Nullable;"
                        )
                )
        );
        String output = run("checkstyleMain").getOutput();
        assertThat(output).contains("Prefer org.jspecify.annotations for nullness");
        assertThat(output).contains("BUILD SUCCESSFUL");
    }

    @Test
    void checkstyleAllowsJSpecifyNullness() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions'", "");
        Path source = projectDir.resolve("src/main/java/example/Example.java");
        Files.createDirectories(source.getParent());
        Files.writeString(
                source,
                javaSource(
                        """
                        package example;

                        import org.jspecify.annotations.Nullable;

                        public class Example {

                            @Nullable
                            public String name() {
                                return null;
                            }

                        }
                        """
                )
        );
        assertThat(run("checkstyleMain").getOutput()).contains("BUILD SUCCESSFUL");
    }

    @Test
    void toolchainDefaultsTo25() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.base'", printToolchain());
        assertThat(run("printToolchain").getOutput()).contains("toolchain=25");
    }

    @Test
    void toolchainHonoursTheJavaMajorProperty() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.base'", printToolchain());
        property("conventions.javaMajor = 21");
        assertThat(run("printToolchain").getOutput()).contains("toolchain=21");
    }

    @Test
    void testingAddsJUnitMockitoAndAssertJ() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.testing'", printTestDependencies());

        String output = run("printTestDependencies").getOutput();

        assertThat(output).contains("org.junit:junit-bom:6.1.3");
        assertThat(output).contains("org.junit.jupiter:junit-jupiter");
        assertThat(output).contains("org.mockito:mockito-core:5.23.0");
        assertThat(output).contains("org.assertj:assertj-bom:3.27.7");
        assertThat(output).contains("org.assertj:assertj-core");
        assertThat(output).contains("org.assertj:assertj-guava");
    }

    @Test
    void testingVersionsCanBeConfiguredThroughTheExtension() throws IOException {
        project(
                "id 'java'\n    id 'com.cleanroommc.conventions.testing'",
                """
                conventions {
                    junitVersion = '5.11.4'
                    mockitoVersion = '5.14.2'
                    assertjVersion = '3.26.3'
                }

                """ +
                        printTestDependencies()
        );
        String output = run("printTestDependencies").getOutput();
        assertThat(output).contains("org.junit:junit-bom:5.11.4");
        assertThat(output).contains("org.mockito:mockito-core:5.14.2");
        assertThat(output).contains("org.assertj:assertj-bom:3.26.3");
    }

    @Test
    void benchmarkingIsOptIn() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions'", printBenchmarking());
        assertThat(run("printBenchmarking").getOutput()).contains("benchmarking=absent");
    }

    @Test
    void benchmarkingCanBeEnabledFromTheAggregatePlugin() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions'", printBenchmarking());
        property("conventions.benchmarking = true");
        assertThat(run("printBenchmarking").getOutput()).contains("benchmarking=present");
    }

    @Test
    void jmhVersionCanBeConfiguredThroughTheExtension() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.benchmarking'", "conventions { jmhVersion = '1.36' }\n\n" + printBenchmarkDependencies());
        String output = run("printBenchmarkDependencies").getOutput();
        assertThat(output).contains("org.openjdk.jmh:jmh-core:1.36");
        assertThat(output).contains("org.openjdk.jmh:jmh-generator-annprocess:1.36");
    }

    @Test
    void benchmarkRunsJmhWithoutCompilingTests() throws IOException {
        project(
                "id 'java'\n    id 'com.cleanroommc.conventions.benchmarking'",
                """
                tasks.named('benchmark') {
                    args 'example.BenchmarkSmoke', '-wi', '0', '-i', '1', '-f', '1', '-r', '10ms'
                }
                """
        );
        javaFile(
                "src/main/java/example/Subject.java",
                """
                package example;

                public final class Subject {

                    public static int value() {
                        return 42;
                    }

                }
                """
        );
        javaFile(
                "src/benchmark/java/example/BenchmarkSmoke.java",
                """
                package example;

                import org.openjdk.jmh.annotations.Benchmark;

                public class BenchmarkSmoke {

                    @Benchmark
                    public int measure() {
                        return Subject.value();
                    }

                }
                """
        );
        javaFile(
                "src/test/java/example/BrokenTest.java",
                """
                package example;

                public class BrokenTest {
                    this deliberately does not compile
                }
                """
        );

        String output = run("--configuration-cache", "benchmark").getOutput();

        assertThat(output).contains("example.BenchmarkSmoke.measure");
        assertThat(output).doesNotContain(":compileTestJava");
        assertThat(output).doesNotContain(":test");
        assertThat(output).contains("Configuration cache entry stored.");
    }

    @Test
    void publishingCreatesAMavenPublicationForAPlainJavaProject() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.publishing'", printPublications());
        assertThat(run("printPublications").getOutput()).contains("publications=[maven]");
    }

    @Test
    void repositoryUrlCanBeConfiguredThroughTheExtension() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.publishing'", "conventions { repositoryUrl = 'https://example.com/cleanroom/project' }");
        run("generatePomFileForMavenPublication");
        String pom = Files.readString(projectDir.resolve("build/publications/maven/pom-default.xml"));
        assertThat(pom).contains("<url>https://example.com/cleanroom/project</url>");
        assertThat(pom).contains("<connection>scm:git:https://example.com/cleanroom/project.git</connection>");
    }

    @Test
    void publishingSkipsTheMavenPublicationForAGradlePluginProject() throws IOException {
        project("id 'java-gradle-plugin'\n    id 'com.cleanroommc.conventions.publishing'", printPublications());
        assertThat(run("printPublications").getOutput()).doesNotContain("maven]");
    }

    @Test
    void publishingSkipsTheMavenPublicationWhenTheGradlePluginIsAppliedAfterTheConventions() throws IOException {
        project("id 'com.cleanroommc.conventions.publishing'\n    id 'java-gradle-plugin'", printPublications());
        assertThat(run("printPublications").getOutput()).contains("publications=[pluginMaven]");
    }

    @Test
    void modPublishingIsOptIn() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions'", printMods());
        assertThat(run("printMods").getOutput()).contains("mods=absent");
    }

    @Test
    void modPublishingExposesTheModsExtensionWhenEnabled() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions'", printMods());
        property("conventions.modPublishing = true");
        assertThat(run("printMods").getOutput()).contains("mods=present");
    }

    @Test
    void readingTheModsExtensionRegistersNoPlatform() throws IOException {
        project(
                "id 'java'\n    id 'com.cleanroommc.conventions.mod'",
                """
                conventions.mods { }

                tasks.register('printPlatforms') {
                    def platforms = publishMods.platforms.names.join(',')
                    doLast { println "platforms=[$platforms]" }
                }
                """
        );
        assertThat(run("printPlatforms").getOutput()).contains("platforms=[]");
    }

    @Test
    void configuresTheCurseforgePlatformFromTheDsl() throws IOException {
        project(
                "id 'java'\n    id 'com.cleanroommc.conventions.mod'",
                """
                conventions.mods {
                    curseforge = '123456'
                }

                tasks.register('printPlatforms') {
                    def platforms = publishMods.platforms.names.join(',')
                    doLast { println "platforms=[$platforms]" }
                }
                """
        );
        assertThat(run("printPlatforms").getOutput()).contains("platforms=[curseforge]");
    }

    @Test
    void checkstyleRejectsAJavaFileWithoutTheHeader() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.style'", "");
        Path source = projectDir.resolve("src/main/java/example/Example.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package example;\n\npublic class Example {\n}\n");
        assertThat(runAndFail("checkstyleMain").getOutput()).contains("Missing a header");
    }

    @Test
    void checkstyleAcceptsThePackedLicenseHeader() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.style'", "");
        Path source = projectDir.resolve("src/main/java/example/Example.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, javaSource("package example;\n\npublic class Example {\n}\n"));
        assertThat(run("checkstyleMain").getOutput()).contains("BUILD SUCCESSFUL");
    }

    @Test
    void checkstyleAcceptsAHeaderFromAnotherYear() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.style'", "");
        Path source = projectDir.resolve("src/main/java/example/Example.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, javaSource(LicenseMode.VISIBLE, LicenseYears.of(2021, 2022), "package example;\n\npublic class Example {\n}\n"));
        assertThat(run("checkstyleMain").getOutput()).contains("BUILD SUCCESSFUL");
    }

    @Test
    void checkLicenseFailsWhenTheFileIsMissing() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.license'", "");
        assertThat(runAndFail("checkLicense").getOutput()).contains("Missing LICENSE");
    }

    @Test
    void checkLicensePassesWhenTheFileMatches() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.license'", "");
        Files.writeString(projectDir.resolve("LICENSE"), LicenseMode.VISIBLE.licenseText(LicenseYears.current()));
        assertThat(run("checkLicense").getOutput()).contains("BUILD SUCCESSFUL");
    }

    @Test
    void checkLicenseFailsWhenTheFileDiffers() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.license'", "");
        Files.writeString(projectDir.resolve("LICENSE"), "MIT\n");
        assertThat(runAndFail("checkLicense").getOutput()).contains("does not match CleanroomMC License Version 1.0");
    }

    @Test
    void checkLicenseUsesAChildLicenseBeforeTheRoot() throws IOException {
        int current = Year.now().getValue();
        int begin = current - 5;
        project("id 'java'", "");
        Path child = childProject("id 'java'\n    id 'com.cleanroommc.conventions.license'", "");
        Files.writeString(child.resolve("LICENSE"), LicenseMode.VISIBLE.licenseText(LicenseYears.of(begin, current)));

        assertThat(projectDir.resolve("LICENSE")).doesNotExist();
        assertThat(run(":child:checkLicense").getOutput()).contains("BUILD SUCCESSFUL");
    }

    @Test
    void checkLicenseDoesNotAcceptTheRootWhenTheChildLicenseDiffers() throws IOException {
        project("id 'java'", "");
        Path child = childProject("id 'java'\n    id 'com.cleanroommc.conventions.license'", "");
        Files.writeString(projectDir.resolve("LICENSE"), LicenseMode.VISIBLE.licenseText(LicenseYears.current()));
        Files.writeString(child.resolve("LICENSE"), "MIT\n");

        BuildResult result = runAndFail(":child:checkLicense");

        assertThat(result.getOutput()).contains(child.resolve("LICENSE").toString());
    }

    @Test
    void childExtractionHonorsItsExplicitBeginFrom() throws IOException {
        int current = Year.now().getValue();
        project("id 'java'", "");
        childProject("id 'java'\n    id 'com.cleanroommc.conventions'", "conventions { beginFrom = 2001 }");

        run(":child:extractConventions");

        assertThat(Files.readString(projectDir.resolve("HEADER"))).contains("Copyright (c) 2001-" + current + " CleanroomMC contributors");
        assertThat(Files.readString(projectDir.resolve("LICENSE"))).contains("Copyright © 2001-" + current + " CleanroomMC contributors");
    }

    @Test
    void childExtractionKeepsTheRootYearWhenBeginFromIsImplicit() throws IOException {
        int current = Year.now().getValue();
        int rootBegin = current - 5;
        int childBegin = current - 2;
        project("id 'java'", "");
        Path child = childProject("id 'java'\n    id 'com.cleanroommc.conventions'", "");
        Files.writeString(projectDir.resolve("HEADER"), LicenseMode.VISIBLE.headerText(LicenseYears.of(rootBegin, current - 1)));
        Files.writeString(child.resolve("HEADER"), LicenseMode.VISIBLE.headerText(LicenseYears.of(childBegin, childBegin)));

        run(":child:extractConventions");

        assertThat(Files.readString(projectDir.resolve("HEADER"))).contains("Copyright (c) " + rootBegin + "-" + current + " CleanroomMC contributors");
        assertThat(Files.readString(projectDir.resolve("HEADER"))).doesNotContain("Copyright (c) " + childBegin + "-" + current + " CleanroomMC contributors");
    }

    @Test
    void publishingDeclaresTheSelectedLicense() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.publishing'", "");
        run("generatePomFileForMavenPublication");
        String pom = Files.readString(projectDir.resolve("build/publications/maven/pom-default.xml"));
        assertThat(pom).contains(LicenseMode.VISIBLE.displayName());
        assertThat(pom).contains(LicenseMode.VISIBLE.url());
        assertThat(pom).contains(LicenseMode.VISIBLE.comments());
    }

    @Test
    void unknownLicenseModeFailsConfiguration() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions.license'", "");
        property("conventions.license = proprietary");
        assertThat(runAndFail("help").getOutput()).contains("Expected free, open or visible");
    }

    @Test
    void theConfigurationCacheIsReusedAcrossBuilds() throws IOException {
        project("id 'java'\n    id 'com.cleanroommc.conventions'", "");

        run("--configuration-cache", "assemble");
        BuildResult second = run("--configuration-cache", "assemble");

        assertThat(second.getOutput()).contains("Reusing configuration cache.");
    }

    private static String javaSource(String body) {
        return javaSource(LicenseMode.VISIBLE, body);
    }

    private static String javaSource(LicenseMode license, String body) {
        return javaSource(license, LicenseYears.current(), body);
    }

    private static String javaSource(LicenseMode license, LicenseYears years, String body) {
        return license.javaHeader(years) + "\n\n" + body;
    }

    private static String printToolchain() {
        return """
                tasks.register('printToolchain') {
                    def version = java.toolchain.languageVersion.get().asInt()
                    doLast { println "toolchain=$version" }
                }
                """;
    }

    private static String printTestDependencies() {
        return """
                tasks.register('printTestDependencies') {
                    def coordinates = configurations.testImplementation.dependencies.collect {
                        [it.group, it.name, it.version].findAll { part -> part != null }.join(':')
                    }
                    doLast { coordinates.each { println "dependency=$it" } }
                }
                """;
    }

    private static String printBenchmarking() {
        return """
                tasks.register('printBenchmarking') {
                    def sourceSet = sourceSets.findByName('benchmark')
                    def benchmarkTask = tasks.findByName('benchmark')
                    def present = sourceSet != null && benchmarkTask != null ? 'present' : 'absent'
                    doLast { println "benchmarking=$present" }
                }
                """;
    }

    private static String printBenchmarkDependencies() {
        return """
                tasks.register('printBenchmarkDependencies') {
                    def implementation = configurations.benchmarkImplementation.dependencies
                    def annotationProcessor = configurations.benchmarkAnnotationProcessor.dependencies
                    def coordinates = (implementation + annotationProcessor).collect {
                        [it.group, it.name, it.version].findAll { part -> part != null }.join(':')
                    }
                    doLast { coordinates.each { println "dependency=$it" } }
                }
                """;
    }

    private static String printPublications() {
        return """
                tasks.register('printPublications') {
                    def names = publishing.publications.names.join(',')
                    doLast { println "publications=[$names]" }
                }
                """;
    }

    private static String printMods() {
        return """
                tasks.register('printMods') {
                    def present = conventions.extensions.findByName('mods') != null ? 'present' : 'absent'
                    doLast { println "mods=$present" }
                }
                """;
    }

    private static String printGroup() {
        return """
                tasks.register('printGroup') {
                    def value = project.group
                    doLast { println "group=$value" }
                }
                """;
    }

    private static String printCompileOnly() {
        return """
                tasks.register('printCompileOnly') {
                    def coordinates = configurations.compileOnly.dependencies.collect {
                        [it.group, it.name, it.version].findAll { part -> part != null }.join(':')
                    }
                    doLast { coordinates.each { println "dependency=$it" } }
                }
                """;
    }

    private static String printManifest() {
        return """
                tasks.named('jar').configure {
                    doLast {
                        def file = archiveFile.get().asFile
                        def attrs = new java.util.jar.JarFile(file).manifest.mainAttributes
                        println "title=${attrs.getValue('Implementation-Title')}"
                        println "version=${attrs.getValue('Implementation-Version')}"
                        println "vendor=${attrs.getValue('Implementation-Vendor')}"
                        println "vendorId=${attrs.getValue('Implementation-Vendor-Id')}"
                        println "specVendor=${attrs.getValue('Specification-Vendor')}"
                    }
                }
                """;
    }

    private static String printSettings(String what) {
        return """
                plugins {
                    id 'com.cleanroommc.conventions.settings'
                }
                rootProject.name = 'conventions-under-test'
                gradle.settingsEvaluated { s ->
                    if ('foojay'.equals('%s')) {
                        println "foojay=${s.pluginManager.hasPlugin('org.gradle.toolchains.foojay-resolver-convention')}"
                    }
                }
                """.formatted(
                what
        );
    }

    private void project(String plugins, String body) throws IOException {
        Files.writeString(
                projectDir.resolve("settings.gradle"),
                """
                plugins {
                    id 'com.cleanroommc.conventions.settings'
                }
                rootProject.name = 'conventions-under-test'
                """
        );
        Files.writeString(projectDir.resolve("build.gradle"), "plugins {\n    " + plugins + "\n}\n\n" + body);
        Files.writeString(projectDir.resolve("gradle.properties"), "versioning.stage = release\n");
        initRepository();
    }

    private Path childProject(String plugins, String body) throws IOException {
        Files.writeString(
                projectDir.resolve("settings.gradle"),
                """
                plugins {
                    id 'com.cleanroommc.conventions.settings'
                }
                rootProject.name = 'conventions-under-test'
                include 'child'
                """
        );
        Files.writeString(projectDir.resolve("build.gradle"), "");
        Path child = Files.createDirectories(projectDir.resolve("child"));
        Files.writeString(child.resolve("build.gradle"), "plugins {\n    " + plugins + "\n}\n\n" + body);
        return child;
    }

    // Cleanroom Versioning computes the version from Git, so every conventions consumer needs a repository with a commit.
    private void initRepository() throws IOException {
        git("init", "-b", "master");
        // Everything the build writes stays untracked and excluded, so the worktree never flips dirty mid-test
        // and the version stays a stable configuration cache input.
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

    private void settingsProject(String settings, String body) throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle"), settings);
        Files.writeString(projectDir.resolve("build.gradle"), body);
    }

    private void property(String line) throws IOException {
        Path properties = projectDir.resolve("gradle.properties");
        Files.writeString(properties, Files.readString(properties) + line + "\n");
    }

    private void javaFile(String relativePath, String body) throws IOException {
        Path source = projectDir.resolve(relativePath);
        Files.createDirectories(source.getParent());
        Files.writeString(source, javaSource(body));
    }

    private BuildResult run(String... arguments) {
        return runner(arguments).build();
    }

    private BuildResult runAndFail(String... arguments) {
        return runner(arguments).buildAndFail();
    }

    private GradleRunner runner(String... arguments) {
        // Under Actions the test project would be versioned as the outer repository's branch or release tag
        Map<String, String> environment = new HashMap<>(System.getenv());
        GITHUB_ENVIRONMENT.forEach(environment::remove);
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .withEnvironment(environment)
                .forwardOutput();
    }

}
