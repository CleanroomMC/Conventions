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

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

enum ConventionsProperty {

    JAVA_VERSION("conventions.javaMajor"),
    MOD_PUBLISHING("conventions.modPublishing"),
    REPO_URL("conventions.repoUrl"),
    CHECKSTYLE_VERSION("conventions.checkstyleVersion"),
    JUNIT_VERSION("conventions.junitVersion"),
    MOCKITO_VERSION("conventions.mockitoVersion"),
    ASSERTJ_VERSION("conventions.assertjVersion"),
    JMH_VERSION("conventions.jmhVersion"),
    BENCHMARKING("conventions.benchmarking"),
    JSPECIFY_VERSION("conventions.jspecifyVersion"),
    JETBRAINS_ANNOTATIONS_VERSION("conventions.jetbrainsAnnotationsVersion"),
    ANONE_VERSION("conventions.anoneVersion"),
    PROVISION_JAVA("conventions.provisionJava"),
    LICENSE("conventions.license");

    private final String key;

    ConventionsProperty(String key) {
        this.key = key;
    }

    Provider<String> provider(ProviderFactory providers) {
        return providers.gradleProperty(key);
    }

    Provider<String> provider(Project project) {
        return provider(project.getProviders());
    }

    String get(Project project, String defaultValue) {
        return provider(project).getOrElse(defaultValue);
    }

    boolean flag(ProviderFactory providers, boolean defaultValue) {
        return provider(providers).map(Boolean::parseBoolean).getOrElse(defaultValue);
    }

    @Override
    public String toString() {
        return key;
    }

}
