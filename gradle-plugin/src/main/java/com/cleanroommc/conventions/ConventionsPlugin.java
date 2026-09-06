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

import com.cleanroommc.tokenenvoy.TokenEnvoyPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * The default overlay for a new CleanroomMC library, mod or tool.
 */
public class ConventionsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(ConventionsBasePlugin.class);
        project.getPluginManager().apply(TokenEnvoyPlugin.class);
        project.getPluginManager().apply(ConventionsLicensePlugin.class);
        project.getPluginManager().apply(ConventionsStylePlugin.class);
        project.getPluginManager().apply(ConventionsAnnotationsPlugin.class);
        project.getPluginManager().apply(ConventionsTestingPlugin.class);
        project.getPluginManager().apply(ConventionsPublishingPlugin.class);
        if (ConventionsProperty.BENCHMARKING.flag(project.getProviders(), false)) {
            project.getPluginManager().apply(ConventionsBenchmarkingPlugin.class);
        }
        if (ConventionsProperty.MOD_PUBLISHING.flag(project.getProviders(), false)) {
            project.getPluginManager().apply(ConventionsModPlugin.class);
        }
    }

}
