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

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Prepends the selected license header to every Java source that does not already start with it.
 */
@UntrackedTask(because = "The task writes into its source files in place")
public abstract class ApplyLicenseHeaderTask extends DefaultTask {

    static final String NAME = "applyLicenseHeader";

    static void register(Project project, LicenseMode license) {
        ConventionsExtension conventions = ConventionsExtension.register(project);
        project.getTasks().register(NAME, ApplyLicenseHeaderTask.class, task -> {
            task.setGroup("formatting");
            task.setDescription("Prepends the selected license header to Java sources that lack it.");
            task.getSource().from(project.provider(() -> sources(project, conventions.getHeader())));
            task.getHeader().convention(CopyrightNotice.provider(project).map(license::javaHeader));
            task.getHeaderPattern().convention(conventions.getAuthor().map(license::javaHeaderPattern));
        });
    }

    private static FileCollection sources(Project project, ConventionsExtension.HeaderExtension header) {
        FileCollection files = project.files();
        JavaPluginExtension java = project.getExtensions().findByType(JavaPluginExtension.class);
        if (java == null) {
            return files;
        }
        List<String> selected = header.getSourceSets().get();
        Path buildDirectory = project.getLayout().getBuildDirectory().get().getAsFile().toPath();
        for (SourceSet sourceSet : java.getSourceSets()) {
            if (selected.isEmpty() || selected.contains(sourceSet.getName())) {
                files = files.plus(
                    sourceSet.getAllJava()
                        .matching(patterns -> patterns.include(header.getIncludes().get())
                            .exclude(header.getExcludes().get()))
                        .filter(file -> file.getName().endsWith(".java") && !file.toPath().startsWith(buildDirectory))
                );
            }
        }
        return files;
    }

    static FileCollection skipped(Project project, ConventionsExtension.HeaderExtension header) {
        FileCollection files = project.files();
        JavaPluginExtension java = project.getExtensions().findByType(JavaPluginExtension.class);
        if (java == null) {
            return files;
        }
        for (SourceSet sourceSet : java.getSourceSets()) {
            files = files.plus(sourceSet.getAllJava().matching(patterns -> patterns.include("**/*.java")));
        }
        return files.minus(sources(project, header));
    }

    @Internal
    public abstract ConfigurableFileCollection getSource();

    @Input
    public abstract Property<String> getHeader();

    @Input
    public abstract Property<String> getHeaderPattern();

    @TaskAction
    public final void apply() throws IOException {
        Pattern pattern = Pattern.compile(getHeaderPattern().get(), Pattern.MULTILINE);
        String header = getHeader().get() + "\n\n";
        int applied = 0;
        for (File file : getSource()) {
            Path path = file.toPath();
            String source = Files.readString(path, StandardCharsets.UTF_8);
            if (!pattern.matcher(source.replace("\r\n", "\n")).lookingAt()) {
                Files.writeString(path, header + source, StandardCharsets.UTF_8);
                applied++;
            }
        }
        getLogger().lifecycle("Applied the license header to {} file(s).", applied);
    }

}
