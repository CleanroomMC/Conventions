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

import java.util.List;
import com.cleanroommc.versioning.gradle.CleanroomVersioningPlugin;
import com.cleanroommc.versioning.gradle.VersioningExtension;
import me.modmuss50.mpp.ModPublishExtension;
import me.modmuss50.mpp.ReleaseType;
import me.modmuss50.mpp.platforms.curseforge.Curseforge;
import me.modmuss50.mpp.platforms.modrinth.Modrinth;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.bundling.Jar;

public class ConventionsModPlugin implements Plugin<Project> {

    private static final String MOD_PUBLISH_PLUGIN_ID = "me.modmuss50.mod-publish-plugin";

    @Override
    public void apply(Project project) {
        // Apply Cleanroom Versioning
        project.getPluginManager().apply(CleanroomVersioningPlugin.class);
        // Apply mod-publish-plugin
        project.getPluginManager().apply(MOD_PUBLISH_PLUGIN_ID);
        ModPublishExtension publishing = project.getExtensions().getByType(ModPublishExtension.class);
        ConventionsExtension.register(project).registerMods(publishing);

        // Apply "forge" as default mod loader, TODO: Cleanroom when applicable
        publishing.getModLoaders().convention(List.of("forge"));
        // Apply output of "jar" task as the publishing file by default
        project.getPlugins()
                .withType(JavaPlugin.class, _ -> publishing.getFile().convention(project.getTasks().named("jar", Jar.class).flatMap(Jar::getArchiveFile)));
        // Set 5 retries as default
        publishing.getMaxRetries().convention(5);
        // Set release type as the one gotten from Cleanroom Versioning
        publishing.getType().convention(
                project.getExtensions().getByType(VersioningExtension.class).getStage().map(stage -> switch (stage) {
                    case ALPHA -> ReleaseType.ALPHA;
                    case BETA, RC -> ReleaseType.BETA;
                    case RELEASE -> ReleaseType.STABLE;
                })
        );

        // Apply "1.12.2" as default mc version, token env var: "CURSEFORGE_TOKEN"
        publishing.getPlatforms().withType(Curseforge.class).configureEach(curseforge -> {
            curseforge.getAccessToken().convention(project.getProviders().environmentVariable("CURSEFORGE_TOKEN"));
            curseforge.getMinecraftVersions().convention(List.of("1.12.2"));
        });
        // Apply "1.12.2" as default mc version, token env var: "MODRINTH_TOKEN"
        publishing.getPlatforms().withType(Modrinth.class).configureEach(modrinth -> {
            modrinth.getAccessToken().convention(project.getProviders().environmentVariable("MODRINTH_TOKEN"));
            modrinth.getMinecraftVersions().convention(List.of("1.12.2"));
        });
    }

}
