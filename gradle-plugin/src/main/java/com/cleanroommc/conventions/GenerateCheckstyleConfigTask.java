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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@CacheableTask
public abstract class GenerateCheckstyleConfigTask extends DefaultTask {

    static final String NAME = "generateConventionsCheckstyleConfig";

    @Input
    public abstract Property<String> getContents();

    // The generated configuration names these files by absolute path.
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getHeaderExempt();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public final void generate() throws IOException {
        Path output = getOutputFile().getAsFile().get().toPath();
        Files.createDirectories(output.getParent());
        Files.writeString(
            output,
            ConventionsFile.exemptFromHeader(getContents().get(), getHeaderExempt().getFiles()),
            StandardCharsets.UTF_8
        );
    }

}
