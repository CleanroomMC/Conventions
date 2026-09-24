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

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.jvm.toolchain.JavaToolchainService;

/**
 * Benchmarking Conventions plugin.
 */
public class ConventionsBenchmarkingPlugin implements Plugin<Project> {

    private static final String BENCHMARK_SOURCE_SET_NAME = "benchmark";

    @Override
    public void apply(Project project) {
        ConventionsExtension conventions = ConventionsExtension.register(project);
        project.getPlugins().withType(JavaPlugin.class, _ -> configureBenchmarking(project, conventions));
    }

    private void configureBenchmarking(Project project, ConventionsExtension conventions) {
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        SourceSetContainer sourceSets = java.getSourceSets();
        NamedDomainObjectProvider<SourceSet> main = sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME);
        NamedDomainObjectProvider<SourceSet> benchmark = sourceSets.register(BENCHMARK_SOURCE_SET_NAME, sourceSet -> {
            FileCollection mainOutput = project.files(main.map(SourceSet::getOutput));
            FileCollection mainCompileClasspath = project.files(main.map(SourceSet::getCompileClasspath));
            FileCollection mainRuntimeClasspath = project.files(main.map(SourceSet::getRuntimeClasspath));
            sourceSet.setCompileClasspath(sourceSet.getCompileClasspath().plus(mainOutput).plus(mainCompileClasspath));
            sourceSet.setRuntimeClasspath(sourceSet.getRuntimeClasspath().plus(mainRuntimeClasspath));
        });

        DependencyHandler dependencies = project.getDependencies();
        benchmark.configure(sourceSet -> {
            dependencies.addProvider(
                sourceSet.getImplementationConfigurationName(),
                conventions.getJmhVersion().map(version -> "org.openjdk.jmh:jmh-core:" + version)
            );
            dependencies.addProvider(
                sourceSet.getAnnotationProcessorConfigurationName(),
                conventions.getJmhVersion().map(version -> "org.openjdk.jmh:jmh-generator-annprocess:" + version)
            );
        });

        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        TaskContainer tasks = project.getTasks();
        tasks.register(BENCHMARK_SOURCE_SET_NAME, JavaExec.class, task -> {
            task.setGroup("benchmark");
            task.setDescription("Runs the JMH benchmark suite.");
            task.getMainClass().set("org.openjdk.jmh.Main");
            task.setClasspath(project.files(benchmark.map(SourceSet::getRuntimeClasspath)));
            task.getJavaLauncher().set(toolchains.launcherFor(java.getToolchain()));
        });
    }

}
