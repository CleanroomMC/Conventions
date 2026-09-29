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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.logging.TestExceptionFormat;
import org.gradle.api.tasks.testing.logging.TestLogEvent;
import org.gradle.api.tasks.testing.logging.TestLoggingContainer;

/**
 * Testing Conventions plugin.
 */
public class ConventionsTestingPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        ConventionsExtension conventions = ConventionsExtension.register(project);
        // Apply JUnit Platform Launcher, Jupiter, Mockito, AssertJ dependencies
        project.getPlugins().withType(JavaPlugin.class, _ -> {
            DependencyHandler dependencies = project.getDependencies();
            String testImplementation = JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME;

            dependencies.addProvider(testImplementation, conventions.getJunitVersion().map(version -> dependencies.platform("org.junit:junit-bom:" + version)));
            dependencies.add(testImplementation, "org.junit.jupiter:junit-jupiter");
            dependencies.add(testImplementation, "org.junit.platform:junit-platform-launcher");

            dependencies.addProvider(testImplementation, conventions.getMockitoVersion().map(version -> "org.mockito:mockito-core:" + version));
            dependencies.addProvider(testImplementation, conventions.getMockitoVersion().map(version -> "org.mockito:mockito-junit-jupiter:" + version));

            dependencies.addProvider(testImplementation, conventions.getAssertjVersion().map(version -> dependencies.platform("org.assertj:assertj-bom:" + version)));
            dependencies.add(testImplementation, "org.assertj:assertj-core");
            dependencies.add(testImplementation, "org.assertj:assertj-guava");
        });

        // Force JUnit Platform on all Tests
        project.getTasks().withType(Test.class).configureEach(test -> {
            test.useJUnitPlatform();
            TestLoggingContainer logging = test.getTestLogging();
            logging.events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED);
            logging.setExceptionFormat(TestExceptionFormat.FULL);
            logging.setShowExceptions(true);
            logging.setShowCauses(true);
            logging.setShowStackTraces(true);
        });
    }

}
