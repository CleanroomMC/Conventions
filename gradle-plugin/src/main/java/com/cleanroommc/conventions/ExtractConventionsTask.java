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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * Writes packed convention files into a directory. Not attached to {@code build}, {@code check} or
 * {@code assemble}. Wire it from a project-specific setup task with {@code dependsOn}.
 */
@UntrackedTask(because = "Writes convention files into the project directory, which clean must not delete")
public abstract class ExtractConventionsTask extends DefaultTask {

    static final String NAME = "extractConventions";
    static final String REGION_BEGIN = "# >>> cleanroom-conventions";
    static final String REGION_END = "# <<< cleanroom-conventions";

    static void register(Project project) {
        if (project.getTasks().getNames().contains(NAME)) {
            return;
        }
        LicenseMode license = LicenseMode.from(project);
        Provider<LicenseYears> years = LicenseYears.provider(project, project.getRootProject());
        project.getTasks().register(NAME, ExtractConventionsTask.class, task -> {
            task.setGroup("conventions");
            task.setDescription("Writes convention files into the project directory. Not attached to build, check or assemble.");
            task.getDestinationDirectory().convention(project.getRootProject().getLayout().getProjectDirectory());
            for (ConventionsFile file : ConventionsFile.values()) {
                if (file == ConventionsFile.CHECKSTYLE) {
                    task.getContents().put(file.fileName(), ConventionsFile.checkstyle(license));
                } else {
                    task.getContents().put(file.fileName(), file.read());
                }
                if (file.mergeMarkedRegion()) {
                    task.getMergedPaths().add(file.fileName());
                }
                task.getOutputFiles().from(task.getDestinationDirectory().file(file.fileName()));
            }
            task.getContents().put("LICENSE", years.map(license::licenseText));
            task.getContents().put("HEADER", years.map(license::headerText));
            task.getOutputFiles().from(task.getDestinationDirectory().file("LICENSE"));
            task.getOutputFiles().from(task.getDestinationDirectory().file("HEADER"));
        });
    }

    @Input
    public abstract MapProperty<String, String> getContents();

    @Input
    public abstract SetProperty<String> getMergedPaths();

    @Internal
    public abstract DirectoryProperty getDestinationDirectory();

    @OutputFiles
    public abstract ConfigurableFileCollection getOutputFiles();

    @TaskAction
    public final void extract() throws IOException {
        Path destination = getDestinationDirectory().getAsFile().get().toPath();
        Set<String> merged = getMergedPaths().get();
        for (Map.Entry<String, String> entry : getContents().get().entrySet()) {
            Path target = destination.resolve(entry.getKey());
            String incoming = entry.getValue();
            String outgoing = incoming;
            if (merged.contains(entry.getKey()) && Files.exists(target)) {
                outgoing = mergeMarkedRegion(Files.readString(target), incoming, entry.getKey());
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, outgoing, StandardCharsets.UTF_8);
        }
    }

    static String mergeMarkedRegion(String existing, String incoming, String fileName) {
        String block = region(incoming);
        int begin = existing.indexOf(REGION_BEGIN);
        int end = existing.indexOf(REGION_END);
        if (begin >= 0 && end < begin) {
            throw new GradleException(
                    fileName + " opens '" + REGION_BEGIN + "' without a matching '" + REGION_END + "'. Repair or delete the region and run " + NAME + " again."
            );
        }
        if (begin >= 0) {
            int after = end + REGION_END.length();
            if (after < existing.length() && existing.charAt(after) == '\n') {
                after++;
            }
            String prefix = existing.substring(0, begin);
            String suffix = existing.substring(after);
            if (!block.endsWith("\n") && !suffix.isEmpty()) {
                return prefix + block + "\n" + suffix;
            }
            return prefix + block + suffix;
        }
        if (!block.endsWith("\n")) {
            return block + "\n" + existing;
        }
        return block + existing;
    }

    private static String region(String content) {
        int begin = content.indexOf(REGION_BEGIN);
        int end = content.indexOf(REGION_END);
        if (begin < 0 || end < begin) {
            return content;
        }
        int after = end + REGION_END.length();
        if (after < content.length() && content.charAt(after) == '\n') {
            after++;
        }
        return content.substring(begin, after);
    }

}
