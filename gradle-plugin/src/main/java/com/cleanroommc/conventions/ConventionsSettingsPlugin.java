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
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.initialization.Settings;
import org.gradle.toolchains.foojay.FoojayToolchainsConventionPlugin;

public class ConventionsSettingsPlugin implements Plugin<Settings> {

    private static void addRepositories(RepositoryHandler repos) {
        repos.mavenCentral();
        repos.gradlePluginPortal();
        repos.exclusiveContent(exclusive -> {
            exclusive.forRepository(() -> repos.maven(maven -> {
                maven.setName(ConventionsDefaults.MAVEN_REPOSITORY_NAME);
                maven.setUrl(ConventionsDefaults.MAVEN_REPOSITORY_URL);
            }));
            exclusive.filter(content -> {
                for (String group : ConventionsDefaults.MAVEN_GROUPS) {
                    content.includeGroup(group);
                }
            });
        });
    }

    @Override
    public void apply(Settings settings) {
        settings.getGradle().getLifecycle().beforeProject(project -> addRepositories(project.getRepositories()));
        if (ConventionsProperty.PROVISION_JAVA.flag(settings.getProviders(), false)) {
            settings.getPluginManager().apply(FoojayToolchainsConventionPlugin.class);
        }
    }

}
