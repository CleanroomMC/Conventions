# AGENTS.md

Instructions for working with Cleanroom's Conventions.

## Overview

This repository holds the convention files every CleanroomMC project shares, the Gradle plugin that applies them, and the reusable GitHub Actions workflows.
[`README.md`](README.md) is the reference for every plugin ID, property, task and workflow input.
Read the relevant section there before acting.

An agent meets this file in one of three situations:

- It works on this repository itself. See [Working on this repository](#working-on-this-repository).
- It updates Conventions in a project that already uses it. See [Updating Conventions in a project](#updating-conventions-in-a-project).
- It migrates an existing project onto Conventions. See [Migrating a project to Conventions](#migrating-a-project-to-conventions).

## Layout

| Path                                            | Contents                                              |
|-------------------------------------------------|-------------------------------------------------------|
| `checkstyle.xml`, `formatj.toml`                | Style rules, packed into the plugin jar               |
| `cliff.toml`                                    | Changelog layout for git-cliff                        |
| `LICENSE`, `HEADER`, `licenses/`                | License texts and Java headers for each license mode  |
| `.editorconfig`, `.gitattributes`, `.gitignore` | Shared dotfiles, also used by this repository         |
| `gradle-plugin/`                                | The Gradle plugin build                               |
| `gradle-plugin/packed-conventions.properties`   | The list of root files packed into the jar            |
| `gradle-plugin/gradle.properties`               | Versions of the plugins each Conventions release pins |
| `.github/workflows/build.yml`, `release.yml`    | Reusable workflows that other projects call           |
| `.github/workflows/ci.yml`, `publish.yml`       | This repository's own callers of those workflows      |

## Working on this Repository

### Commands

Run Gradle from `gradle-plugin/`. The plugin needs Java 25.

```shell
cd gradle-plugin
./gradlew compileJava                                  # after a class change
./gradlew test --tests '*ExtractConventionsTaskTest'   # one test class
./gradlew build                                        # full check before a pull request
./gradlew applyLicenseHeader clearSkiesApply formatJavaApply
```

Run the narrowest task that covers the change. The functional tests start real Gradle builds and are slow.

### Rules

- The plugin applies its own conventions to itself, so `build` runs Checkstyle, FormatJ and `checkLicense` on the plugin sources.
- The configuration cache is on and its problems fail the build. Keep tasks and plugins compatible with it.
- A convention file at the repository root reaches consumers only when it is listed in `gradle-plugin/packed-conventions.properties`. Add a new file there.
- A change to a root convention file changes every consuming project on its next update. Add or adjust a functional test for it.
- A new plugin, property, task or workflow input needs a matching entry in `README.md`.
- Do not edit `CHANGELOG.md`. The release workflow generates it from commit messages.
- Do not tag, publish or push to `master`. Releases are cut by a maintainer.
- Ask before adding a dependency or bumping a version in `gradle-plugin/gradle.properties`.

### Commits and Branches

- Commit subjects read `type(scope): summary`. This repository writes `feature`, `fix`, `refactor`, `doc`, `test`, `build`, `ci`, `chore` and `style`.
- `chore` and `style` commits are left out of the changelog, except `chore(deps)`, which is listed under Build and Dependencies.
- Branches follow [Branches](README.md#branches). Work goes on `feature/<slug>` or `fix/<slug>` and merges through a pull request.

## Updating Conventions in a Project

These steps move a project that already applies Conventions to a newer release. Run them in the consuming project.

1. Read [`CHANGELOG.md`](CHANGELOG.md) from the pinned version up to the target version. A major version bump carries breaking changes.
2. Change the version of `com.cleanroommc.conventions.settings` in `settings.gradle`. It is the only place the version is declared, and the project plugins follow it.
3. Change the `@` ref of every `CleanroomMC/Conventions/.github/workflows/*.yml` call under `.github/workflows` to the same tag.
4. Run `./gradlew extractConventions` and review `git diff`. The task overwrites the extracted files, and in `.gitignore` it only replaces the `# >>> cleanroom-conventions` region.
5. Run `./gradlew applyLicenseHeader clearSkiesApply formatJavaApply`. A release that changes the header or the style rules rewrites sources here.
6. Run `./gradlew build`. Fix what Checkstyle reports by hand, since it has no apply task.
7. Commit the version bump as `chore(deps)`, and any rewritten sources as a separate `style` commit so the bump stays reviewable.

Rules:

- Do not edit `checkstyle.xml`, `formatj.toml`, `.editorconfig`, `.gitattributes`, `LICENSE`, `HEADER`, or the conventions region of `.gitignore` by hand. `extractConventions` owns them, and the next run discards the edit.
- Do not pin Versioning, Token Envoy, FormatJ, ClearSkies or the publishing plugins in the project. Each Conventions release pins them.
- Do not silence a failing check to get the build green. Report it instead.

## Migrating a Project to Conventions

These steps bring an existing project onto Conventions. Work on a `chore/conventions` branch and keep each numbered step in its own commit, because steps 7 and 8 touch most source files.

1. Check the prerequisites.
   - The project is a Git repository with at least one commit.
   - The Gradle wrapper is at the version this plugin is built against, which is in [`gradle-plugin/gradle/wrapper/gradle-wrapper.properties`](gradle-plugin/gradle/wrapper/gradle-wrapper.properties).
   - Existing release tags read `1.2.3` or `1.2.3-beta`. Versioning computes the version from them and the release workflow only triggers on that shape.
2. Add the settings plugin to `settings.gradle` as shown under [Gradle Plugin](README.md#gradle-plugin), pinned to the latest tag of this repository.
3. Apply `com.cleanroommc.conventions` in `build.gradle`, next to `java`. Apply the individual plugin IDs instead when the project only wants part of the conventions.
4. Remove what Conventions now owns.
   - `version` in `gradle.properties` and in the build script. Versioning computes it from Git tags.
   - `group`, when it is `com.cleanroommc`.
   - Maven Central, Plugin Portal and Cleanroom Maven entries in `repositories { }` and `pluginManagement { }`. Keep any other repository.
   - Toolchain, encoding, `sourcesJar`, `javadocJar`, POM, signing and Cleanroom Maven publishing blocks.
   - JUnit, Mockito, AssertJ, JSpecify, JetBrains Annotations and JMH dependencies, plus `useJUnitPlatform()`.
   - Spotless, a hand-applied Checkstyle, license plugins, and any other formatter. Delete their config files too.
   - Direct applications of Versioning, Token Envoy, `com.gradle.plugin-publish` and `mod-publish-plugin`.
5. Set the properties that differ from the defaults in `gradle.properties`, and project values in a `conventions { }` block. See [Configuration](README.md#configuration).
   - `conventions.license`, which defaults to `visible`. Choose the mode that matches the license the project already ships, since changing a license needs the copyright holders' consent.
   - `conventions.javaMajor`, when the project does not target Java 25. A 1.12.2 Forge mod sets `8`.
   - `conventions.provisionJava = true`, when Gradle should download the toolchain.
   - `beginFrom`, set to the first year in the existing copyright notice.
   - `repositoryUrl`, `author` and `mods { }` where they apply.
6. Run `./gradlew extractConventions` and review `git diff`. Move project-specific `.gitignore` lines outside the `# >>> cleanroom-conventions` region. Delete a local `cliff.toml` unless the project needs its own changelog layout.
7. Run `./gradlew applyLicenseHeader`. It prepends the header and never removes an existing comment, so delete old license headers from the sources first. Use `conventions { header { exclude(...) } }` for vendored code under another license.
8. Run `./gradlew clearSkiesApply formatJavaApply` and commit the result alone as `style`. Add that commit's hash to `.git-blame-ignore-revs`.
9. Run `./gradlew build` and fix what Checkstyle reports. Replace nullness annotations from other libraries with `org.jspecify.annotations` where the advisory names them.
10. Run `./gradlew -q printVersion` and confirm the version continues from the last release tag.
11. Replace the project's workflows with callers of the reusable [Build](README.md#build) and [Release](README.md#release) workflows, pinned to the same tag as the plugin. Add the secrets the chosen publish inputs need.
12. Rename branches to match [Branches](README.md#branches) when the release branch is not `master`, or pass `release-branch` to the build workflow.

Rules:

- Stop and ask before changing `conventions.license`, retagging, renaming branches, or adding repository secrets. These are decisions for the maintainer.
- Keep build logic that Conventions does not cover, such as mod toolchain configuration, shading and custom tasks.
- Do not mix behaviour changes into the migration. A source file should change only through steps 7 to 9.
- Never run a publish task to test the migration. `./gradlew build` and `printVersion` are enough.

## Boundaries

- Never commit secrets, tokens or signing keys. Publishing credentials live in repository secrets only.
- Never run `publish*`, `publishPlugins` or `publishMods` unless the maintainer asks for it in the current task.
- Never rewrite history, force-push, or move a tag.
- When a step fails and the fix is not obvious from the error, stop and report the failing command and its output.
