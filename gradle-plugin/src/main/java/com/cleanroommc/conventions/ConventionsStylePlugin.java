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

import zone.rong.clearskies.gradle.ClearSkiesPlugin;
import zone.rong.formatj.gradle.FormatJExtension;
import zone.rong.formatj.gradle.FormatJPlugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CheckstylePlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

/**
 * Style Conventions plugin.
 */
public class ConventionsStylePlugin implements Plugin<Project> {

    private static final String CHECKSTYLE_VERSION = "14.0.0";

    @Override
    public void apply(Project project) {
        ExtractConventionsTask.register(project);
        LicenseMode license = LicenseMode.from(project);

        PluginManager plugins = project.getPluginManager();
        TaskContainer tasks = project.getTasks();

        // Apply ClearSkies, no configuration needed
        plugins.apply(ClearSkiesPlugin.class);

        // Apply FormatJ and its configuration
        plugins.apply(FormatJPlugin.class);
        project.getExtensions().getByType(FormatJExtension.class).getStyle().set(ConventionsFile.FORMAT_J.read());

        // Apply Checkstyle and its configuration
        plugins.apply(CheckstylePlugin.class);
        CheckstyleExtension checkstyle = project.getExtensions().getByType(CheckstyleExtension.class);
        checkstyle.setToolVersion(ConventionsProperty.CHECKSTYLE_VERSION.get(project, CHECKSTYLE_VERSION));
        TaskProvider<GenerateCheckstyleConfigTask> generateConfig = tasks.register(
                GenerateCheckstyleConfigTask.NAME,
                GenerateCheckstyleConfigTask.class,
                task -> {
                    task.setDescription("Generates Checkstyle configuration for the selected license.");
                    task.getContents().convention(ConventionsFile.checkstyle(license));
                    task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("conventions/checkstyle.xml"));
                }
        );
        checkstyle.setConfig(project.getResources().getText().fromFile(generateConfig.flatMap(GenerateCheckstyleConfigTask::getOutputFile)));

        // ClearSkies expands star imports, FormatJ formats the lines it wrote, Checkstyle judges the result.
        tasks.named(FormatJPlugin.APPLY_TASK_NAME).configure(task -> task.mustRunAfter(ClearSkiesPlugin.APPLY_TASK_NAME));
        tasks.named(FormatJPlugin.CHECK_TASK_NAME).configure(task -> task.mustRunAfter(ClearSkiesPlugin.CHECK_TASK_NAME));
        tasks.withType(Checkstyle.class)
                .configureEach(task -> task.mustRunAfter(
                        ClearSkiesPlugin.APPLY_TASK_NAME,
                        ClearSkiesPlugin.CHECK_TASK_NAME,
                        FormatJPlugin.APPLY_TASK_NAME,
                        FormatJPlugin.CHECK_TASK_NAME
                ));
    }

}
