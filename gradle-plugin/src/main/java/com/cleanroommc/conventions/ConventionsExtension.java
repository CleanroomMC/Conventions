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

import me.modmuss50.mpp.ModPublishExtension;
import me.modmuss50.mpp.platforms.curseforge.Curseforge;
import me.modmuss50.mpp.platforms.modrinth.Modrinth;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Nested;

import java.util.List;
import javax.inject.Inject;

public abstract class ConventionsExtension {

    public static final String NAME = "conventions";

    static ConventionsExtension register(Project project) {
        ExtensionContainer extensions = project.getExtensions();
        ConventionsExtension existing = extensions.findByType(ConventionsExtension.class);
        ConventionsExtension extension = existing;
        if (extension == null) {
            extension = extensions.create(NAME, ConventionsExtension.class);
        }
        extension.getAuthor().convention(ConventionsDefaults.AUTHOR);
        extension.getRepositoryUrl().convention(ConventionsProperty.REPO_URL.provider(project));
        int javaMajor = ConventionsProperty.javaMajor(project);
        extension.getJunitVersion()
            .convention(
                ConventionsProperty.JUNIT_VERSION
                    .provider(project)
                    .orElse(javaMajor < 17 ? ConventionsDefaults.JUNIT_5_VERSION : ConventionsDefaults.JUNIT_VERSION)
            );
        extension.getMockitoVersion()
            .convention(
                ConventionsProperty.MOCKITO_VERSION
                    .provider(project)
                    .orElse(
                        javaMajor < 11 ? ConventionsDefaults.MOCKITO_4_VERSION : ConventionsDefaults.MOCKITO_VERSION
                    )
            );
        extension.getAssertjVersion()
            .convention(
                ConventionsProperty.ASSERTJ_VERSION.provider(project).orElse(ConventionsDefaults.ASSERTJ_VERSION)
            );
        extension.getJmhVersion()
            .convention(ConventionsProperty.JMH_VERSION.provider(project).orElse(ConventionsDefaults.JMH_VERSION));
        extension.getJspecifyVersion()
            .convention(
                ConventionsProperty.JSPECIFY_VERSION.provider(project).orElse(ConventionsDefaults.JSPECIFY_VERSION)
            );
        extension.getJetbrainsAnnotationsVersion()
            .convention(
                ConventionsProperty.JETBRAINS_ANNOTATIONS_VERSION
                    .provider(project)
                    .orElse(ConventionsDefaults.JETBRAINS_ANNOTATIONS_VERSION)
            );
        extension.getAnoneVersion()
            .convention(ConventionsProperty.ANONE_VERSION.provider(project).orElse(ConventionsDefaults.ANONE_VERSION));
        return extension;
    }

    public abstract Property<String> getRepositoryUrl();

    public abstract Property<String> getJunitVersion();

    public abstract Property<String> getMockitoVersion();

    public abstract Property<String> getAssertjVersion();

    public abstract Property<String> getJmhVersion();

    public abstract Property<String> getJspecifyVersion();

    public abstract Property<String> getJetbrainsAnnotationsVersion();

    public abstract Property<String> getAnoneVersion();

    public abstract Property<Integer> getBeginFrom();

    public abstract Property<String> getAuthor();

    @Nested
    public abstract HeaderExtension getHeader();

    public void header(Action<? super HeaderExtension> action) {
        action.execute(getHeader());
    }

    public ModsExtension getMods() {
        return ((ExtensionAware) this).getExtensions().getByType(ModsExtension.class);
    }

    public void mods(Action<? super ModsExtension> action) {
        action.execute(getMods());
    }

    ModsExtension registerMods(ModPublishExtension publishMods) {
        return ((ExtensionAware) this).getExtensions().create(ModsExtension.NAME, ModsExtension.class, publishMods);
    }

    /**
     * Selects the Java sources {@code applyLicenseHeader} writes to.
     */
    public abstract static class HeaderExtension {

        /** Names of the source sets to cover. Defaults to every source set in the project. */
        public abstract ListProperty<String> getSourceSets();

        /** Ant-style patterns of the files to cover, relative to each source directory. Defaults to every file. */
        public abstract SetProperty<String> getIncludes();

        /** Ant-style patterns of the files to leave alone, relative to each source directory. */
        public abstract SetProperty<String> getExcludes();

        public void sourceSets(String... names) {
            getSourceSets().set(List.of(names));
        }

        public void include(String... patterns) {
            getIncludes().addAll(patterns);
        }

        public void exclude(String... patterns) {
            getExcludes().addAll(patterns);
        }

    }

    public abstract static class ModsExtension {

        public static final String NAME = "mods";

        private final ModPublishExtension publishMods;

        @Inject
        public ModsExtension(ModPublishExtension publishMods) {
            this.publishMods = publishMods;
        }

        // Registering happens inside the action, so reading the DSL never creates a platform on its own.
        public void setCurseforge(String projectId) {
            publishMods.curseforge(curseforge -> curseforge.getProjectId().set(projectId));
        }

        public void curseforge(Action<Curseforge> action) {
            publishMods.curseforge(action);
        }

        public void setModrinth(String projectId) {
            publishMods.modrinth(modrinth -> modrinth.getProjectId().set(projectId));
        }

        public void modrinth(Action<Modrinth> action) {
            publishMods.modrinth(action);
        }

    }

}
