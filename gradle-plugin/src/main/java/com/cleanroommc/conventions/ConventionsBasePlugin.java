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

import java.util.Map;
import com.cleanroommc.versioning.gradle.CleanroomVersioningPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;

/**
 * Base Conventions plugin.
 */
public class ConventionsBasePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        PluginManager plugins = project.getPluginManager();
        ExtensionContainer extensions = project.getExtensions();
        TaskContainer tasks = project.getTasks();

        ConventionsExtension.register(project);
        ExtractConventionsTask.register(project);

        // Apply Cleanroom Versioning
        plugins.apply(CleanroomVersioningPlugin.class);

        if (project.getGroup().toString().isEmpty()) {
            project.setGroup(ConventionsDefaults.GROUP);
        }

        // UTF-8 Encoding on all JavaCompile, Javadoc, Test tasks
        tasks.withType(JavaCompile.class).configureEach(task -> task.getOptions().setEncoding("UTF-8"));
        tasks.withType(Test.class).configureEach(task -> task.setDefaultCharacterEncoding("UTF-8"));
        tasks.withType(Javadoc.class).configureEach(task -> {
            task.getOptions().setEncoding("UTF-8");
            // Muted Javadocs 'missing' group, '-quiet' is filler as the option takes no argument
            ((CoreJavadocOptions) task.getOptions()).addStringOption("Xdoclint:all,-missing", "-quiet");
        });

        // Gets "conventions.javaMajor" and sets Java toolchain to it
        project.getPlugins()
                .withType(
                        JavaPlugin.class,
                        _ -> extensions.getByType(JavaPluginExtension.class)
                                .getToolchain()
                                .getLanguageVersion()
                                .set(JavaLanguageVersion.of(ConventionsProperty.JAVA_VERSION.get(project, ConventionsDefaults.JAVA_VERSION)))
                );

        // Verifiable rebuilds
        tasks.withType(AbstractArchiveTask.class).configureEach(task -> {
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);
        });

        tasks.withType(Jar.class).configureEach(jar -> configureManifest(project, jar));

        plugins.apply(IdeaPlugin.class);
        IdeaModel idea = extensions.getByType(IdeaModel.class);
        idea.getModule().setDownloadSources(true);
        idea.getModule().setDownloadJavadoc(true);
    }

    private void configureManifest(Project project, Jar jar) {
        jar.getManifest()
                .attributes(
                        Map.of(
                                "Implementation-Title",
                                project.provider(project::getName),
                                "Implementation-Version",
                                project.provider(() -> project.getVersion().toString()),
                                "Implementation-Vendor",
                                ConventionsDefaults.ORGANIZATION_NAME,
                                "Implementation-Vendor-Id",
                                project.provider(() -> project.getGroup().toString()),
                                "Specification-Title",
                                project.provider(project::getName),
                                "Specification-Version",
                                project.provider(() -> project.getVersion().toString()),
                                "Specification-Vendor",
                                ConventionsDefaults.ORGANIZATION_NAME
                        )
                );
    }

}
