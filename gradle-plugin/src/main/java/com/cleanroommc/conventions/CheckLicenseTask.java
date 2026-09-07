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
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.work.DisableCachingByDefault;

/**
 * Requires {@code LICENSE} in the project directory or a parent directory to match the configured license mode.
 */
@DisableCachingByDefault(because = "License verification is cheap and has no outputs")
public abstract class CheckLicenseTask extends DefaultTask {

    static final String NAME = "checkLicense";

    static void register(Project project) {
        if (project.getTasks().getNames().contains(NAME)) {
            return;
        }
        LicenseMode license = LicenseMode.from(project);
        Provider<LicenseYears> years = LicenseYears.provider(project);
        project.getTasks().register(NAME, CheckLicenseTask.class, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Requires LICENSE in the project directory or a parent directory to match the configured license mode.");
            task.getExpected().convention(years.map(license::licenseText));
            task.getExpectedName().convention(license.displayName());
            task.getStartDirectory().convention(project.getLayout().getProjectDirectory());
        });
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(check -> check.dependsOn(NAME));
    }

    @Input
    public abstract Property<String> getExpected();

    @Input
    public abstract Property<String> getExpectedName();

    @Internal
    public abstract DirectoryProperty getStartDirectory();

    @TaskAction
    public final void check() throws IOException {
        Path start = getStartDirectory().getAsFile().get().toPath();
        Path license = findLicense(start);
        if (license == null) {
            throw new GradleException(
                    "Missing LICENSE. Run extractConventions, or copy " + getExpectedName().get() + " to the project directory or a parent directory."
            );
        }
        String actual = Files.readString(license, StandardCharsets.UTF_8);
        if (!sameLicense(getExpected().get(), actual)) {
            throw new GradleException(license + " does not match " + getExpectedName().get() + ".");
        }
    }

    static Path findLicense(Path start) {
        Path dir = start.toAbsolutePath().normalize();
        while (dir != null) {
            Path candidate = dir.resolve("LICENSE");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    static boolean sameLicense(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }

    static String normalize(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

}
