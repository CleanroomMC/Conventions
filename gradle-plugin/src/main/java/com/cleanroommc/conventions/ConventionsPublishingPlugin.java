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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.authentication.http.BasicAuthentication;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;
import org.gradle.plugins.signing.SigningExtension;
import org.gradle.plugins.signing.SigningPlugin;
import org.gradle.process.ExecOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * Publishing Conventions plugin.
 */
public class ConventionsPublishingPlugin implements Plugin<Project> {

    private static final String MAVEN_PUBLICATION = "maven";
    private static final String JAVA_GRADLE_PLUGIN_ID = "java-gradle-plugin";
    private static final String PLUGIN_PUBLISH_ID = "com.gradle.plugin-publish";

    @Override
    public void apply(Project project) {
        PluginManager plugins = project.getPluginManager();
        ConventionsExtension conventions = ConventionsExtension.register(project);

        project.getPlugins().withType(JavaPlugin.class, _ -> configureJavaPublishing(project));
        project.getPlugins().withType(MavenPublishPlugin.class, _ -> configurePublications(project, conventions));
        plugins.withPlugin(JAVA_GRADLE_PLUGIN_ID, _ -> configurePluginPublishing(project));
    }

    private void configureJavaPublishing(Project project) {
        project.getPluginManager().apply(MavenPublishPlugin.class);

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.withSourcesJar();
        java.withJavadocJar();

        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        project.afterEvaluate(_ -> {
            if (project.getPluginManager().hasPlugin(JAVA_GRADLE_PLUGIN_ID) || publishing.getPublications().findByName(MAVEN_PUBLICATION) != null) {
                return;
            }
            publishing.getPublications()
                .register(MAVEN_PUBLICATION, MavenPublication.class, publication -> publication.from(project.getComponents().getByName("java")));
        });
    }

    private void configurePluginPublishing(Project project) {
        project.getPluginManager().apply(PLUGIN_PUBLISH_ID);
    }

    private void configureSigning(Project project, PublishingExtension publishing) {
        project.getPluginManager().apply(SigningPlugin.class);
        SigningExtension signing = project.getExtensions().getByType(SigningExtension.class);
        Provider<String> signingKey = project.getProviders().gradleProperty("signingKey");
        Provider<String> signingPassword = project.getProviders().gradleProperty("signingPassword");
        boolean enabled = signingKey.isPresent() && signingPassword.isPresent();
        signing.setRequired(enabled);
        if (!enabled) {
            return;
        }
        signing.useInMemoryPgpKeys(signingKey.get(), signingPassword.get());
        signing.sign(publishing.getPublications());
    }

    private void configurePublications(Project project, ConventionsExtension conventions) {
        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        LicenseMode license = LicenseMode.from(project);
        addCleanroomRepository(publishing);
        configureSigning(project, publishing);

        Provider<String> fromProperty = conventions.getRepositoryUrl().map(ConventionsPublishingPlugin::canonicalHttpUrl);
        Provider<String> fromGit = gitUpstreamUrl(project);

        Property<String> repositoryUrl = project.getObjects().property(String.class);
        Property<String> homepageUrl = project.getObjects().property(String.class);
        repositoryUrl.convention(fromProperty.orElse(fromGit));
        homepageUrl.convention(repositoryUrl);

        project.getPluginManager().withPlugin(JAVA_GRADLE_PLUGIN_ID, _ -> {
            GradlePluginDevelopmentExtension gradlePlugin = project.getExtensions().getByType(GradlePluginDevelopmentExtension.class);
            repositoryUrl.convention(fromProperty.orElse(fromGit).orElse(gradlePlugin.getVcsUrl().map(ConventionsPublishingPlugin::canonicalHttpUrl)));
            homepageUrl.convention(gradlePlugin.getWebsite().orElse(repositoryUrl));
        });

        // The POM reads these once per field
        repositoryUrl.finalizeValueOnRead();
        homepageUrl.finalizeValueOnRead();

        publishing.getPublications()
            .withType(MavenPublication.class)
            .configureEach(publication -> configurePom(project, publication, homepageUrl, repositoryUrl, license));
    }

    private void configurePom(
        Project project,
        MavenPublication publication,
        Provider<String> homepageUrl,
        Provider<String> repositoryUrl,
        LicenseMode licenseMode
    ) {
        publication.pom(pom -> {
            pom.getName().convention(project.provider(project::getName));
            pom.getDescription().convention(project.provider(project::getDescription));
            pom.getUrl().convention(homepageUrl);
            pom.organization(organization -> {
                organization.getName().convention(ConventionsDefaults.ORGANIZATION_NAME);
                organization.getUrl().convention(ConventionsDefaults.ORGANIZATION_URL);
            });
            pom.licenses(licenses -> licenses.license(license -> {
                license.getName().convention(licenseMode.displayName());
                license.getUrl().convention(licenseMode.url());
                license.getDistribution().convention("repo");
                if (!licenseMode.comments().isEmpty()) {
                    license.getComments().convention(licenseMode.comments());
                }
            }));
            pom.scm(scm -> {
                scm.getUrl().convention(repositoryUrl);
                scm.getConnection().convention(repositoryUrl.map(url -> "scm:git:" + url + ".git"));
                scm.getDeveloperConnection().convention(repositoryUrl.map(ConventionsPublishingPlugin::scmDeveloperConnection));
            });
        });
    }

    private void addCleanroomRepository(PublishingExtension publishing) {
        if (publishing.getRepositories().findByName(ConventionsDefaults.MAVEN_REPOSITORY_NAME) != null) {
            return;
        }
        publishing.getRepositories().maven(repository -> {
            repository.setName(ConventionsDefaults.MAVEN_REPOSITORY_NAME);
            repository.setUrl(ConventionsDefaults.MAVEN_REPOSITORY_URL);
            repository.credentials(PasswordCredentials.class);
            repository.getAuthentication().create("basic", BasicAuthentication.class);
        });
    }

    private Provider<String> gitUpstreamUrl(Project project) {
        Provider<String> remote = git(project, "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}")
            .filter(ref -> ref.indexOf('/') >= 0)
            .map(ref -> ref.substring(0, ref.indexOf('/')))
            .orElse("origin");
        return remote.flatMap(name -> git(project, "remote", "get-url", name)).map(ConventionsPublishingPlugin::canonicalHttpUrl);
    }

    private Provider<String> git(Project project, String... args) {
        ExecOutput output = project.getProviders().exec(spec -> {
            List<String> commandLine = new ArrayList<>(1 + args.length);
            commandLine.add("git");
            commandLine.addAll(List.of(args));
            spec.commandLine(commandLine);
            spec.workingDir(project.getRootProject().getLayout().getProjectDirectory());
            spec.setIgnoreExitValue(true);
        });
        return output.getResult()
            .filter(result -> result.getExitValue() == 0)
            .flatMap(_ -> output.getStandardOutput().getAsText())
            .map(String::trim)
            .filter(text -> !text.isEmpty());
    }

    private static String canonicalHttpUrl(String url) {
        String canonical = url.trim();
        if (canonical.endsWith(".git")) {
            canonical = canonical.substring(0, canonical.length() - 4);
        }
        if (canonical.startsWith("git@")) {
            int colon = canonical.indexOf(':');
            if (colon > 4) {
                canonical = "https://" + canonical.substring(4, colon) + "/" + canonical.substring(colon + 1);
            }
        } else if (canonical.startsWith("ssh://git@")) {
            canonical = "https://" + canonical.substring("ssh://git@".length());
        } else if (canonical.startsWith("git://")) {
            canonical = "https://" + canonical.substring("git://".length());
        }
        if (canonical.endsWith("/")) {
            canonical = canonical.substring(0, canonical.length() - 1);
        }
        return canonical;
    }

    private static String scmDeveloperConnection(String url) {
        if (url.startsWith("https://")) {
            String hostAndPath = url.substring("https://".length());
            int slash = hostAndPath.indexOf('/');
            if (slash > 0) {
                return "scm:git:git@" + hostAndPath.substring(0, slash) + ":" + hostAndPath.substring(slash + 1) + ".git";
            }
        }
        return "scm:git:" + url + ".git";
    }

}
