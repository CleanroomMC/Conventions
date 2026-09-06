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
import org.gradle.api.plugins.JavaPluginExtension;

/**
 * Annotations Conventions plugin.
 */
public class ConventionsAnnotationsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        ConventionsExtension conventions = ConventionsExtension.register(project);
        project.getPlugins().withType(JavaPlugin.class, _ -> {
            DependencyHandler dependencies = project.getDependencies();
            project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().configureEach(sourceSet -> {
                String compileOnly = sourceSet.getCompileOnlyConfigurationName();
                dependencies.addProvider(compileOnly, conventions.getJspecifyVersion().map(version -> "org.jspecify:jspecify:" + version));
                dependencies.addProvider(compileOnly, conventions.getJetbrainsAnnotationsVersion().map(version -> "org.jetbrains:annotations:" + version));
                dependencies.addProvider(compileOnly, conventions.getAnoneVersion().filter(version -> !version.isEmpty()).map(version -> "com.cleanroommc:anone:" +
                        version));
            });
        });
    }

}
