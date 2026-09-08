# Conventions
Cleanroom's Conventions. Here you can find how a Cleanroom's project is to behave.

Contains the following conventional files.

- `LICENSE`
- `HEADER`
- `licenses/free/LICENSE`, `licenses/free/HEADER` (MIT variant)
- `licenses/open/LICENSE`, `licenses/open/HEADER` (LGPLv3 variant)
- `checkstyle.xml`
- `formatj.toml`
- `cliff.toml`
- `.editorconfig`
- `.gitattributes`
- `.gitignore`

## Gradle Plugin

Published plugin IDs:

| Plugin ID                                  | Purpose                                                        |
|--------------------------------------------|----------------------------------------------------------------|
| `com.cleanroommc.conventions.settings`     | Settings plugin: repositories and optional Foojay              |
| `com.cleanroommc.conventions`              | Applies every project convention plugin                        |
| `com.cleanroommc.conventions.base`         | Versioning, Java, encoding, & reproducible archive conventions |
| `com.cleanroommc.conventions.license`      | Requires LICENSE to match the selected license conventions    |
| `com.cleanroommc.conventions.style`        | Code formatting & Checkstyle conventions                       |
| `com.cleanroommc.conventions.annotations`  | JSpecify, JetBrains Annotations & AnoNe conventions            |
| `com.cleanroommc.conventions.testing`      | JUnit, AssertJ & Mockito conventions                           |
| `com.cleanroommc.conventions.benchmarking` | JMH benchmarking suite in an isolated `benchmark` source set   |
| `com.cleanroommc.conventions.publishing`   | Maven & Gradle Plugin Portal publishing conventions            |
| `com.cleanroommc.conventions.mod`          | CurseForge & Modrinth publishing conventions                   |

```groovy filename="settings.gradle"
pluginManagement {
    repositories {
        maven {
            url = 'https://maven.cleanroommc.com'
        }
        gradlePluginPortal()
    }
}

plugins {
    id 'com.cleanroommc.conventions.settings' version '1.0.0'
}
```

```groovy filename="build.gradle"
plugins {
    id 'java'
    id 'com.cleanroommc.conventions'
}
```

The aggregate plugin also applies [Token Envoy](https://github.com/CleanroomMC/GradleTokenEnvoy) 1.1.0. Configure its `tokenEnvoy` extension to replace `@{NAME}` tokens in compiled classes and resources without rewriting source files. The Token Envoy version is pinned by each Conventions release and cannot be overridden per project. Its `@{NAME}` syntax is unrelated to the `@YEAR@` and `@LICENSE_HEADER@` placeholders used by license templating.

A `repositories { }` block in `build.gradle` is allowed and only appends. It cannot replace Maven Central, the Plugin Portal, or Cleanroom Maven.

### Configuration

These values are read before the project DSL, so they remain Gradle properties. The
license mode in particular is resolved while plugins apply, before the `conventions { }`
extension block below evaluates.

| Property                        | Default    | Behaviour                                         |
|---------------------------------|------------|---------------------------------------------------|
| `conventions.license`           | `visible`  | `free` (MIT), `open` (LGPLv3), or `visible`       |
| `conventions.javaMajor`         | `25`       | Java toolchain language version                   |
| `conventions.provisionJava`     | `false`    | Settings plugin applies Foojay toolchain resolver |
| `conventions.modPublishing`     | `false`    | Applies the mod conventions                       |
| `conventions.benchmarking`      | `false`    | Applies the benchmarking conventions              |
| `conventions.checkstyleVersion` | `14.0.0`   | Checkstyle version                                |

Project-level dependency, publishing and copyright values belong to the managed `conventions` extension:

```groovy filename="build.gradle"
conventions {
    beginFrom = 2021
    repositoryUrl = 'https://github.com/CleanroomMC/example'
    junitVersion = '6.1.3'
    mockitoVersion = '5.23.0'
    assertjVersion = '3.27.7'
    jmhVersion = '1.37'
    jspecifyVersion = '1.0.0'
    jetbrainsAnnotationsVersion = '26.1.0'
    anoneVersion = '1.0.0'
}
```

The existing `conventions.repoUrl`, `conventions.junitVersion`, `conventions.mockitoVersion`, `conventions.assertjVersion`, `conventions.jmhVersion`, `conventions.jspecifyVersion`, `conventions.jetbrainsAnnotationsVersion` and `conventions.anoneVersion` Gradle properties remain supported as defaults for compatibility and CI overrides. An extension value takes precedence.

> [!IMPORTANT]
> Cleanroom Versioning 3 is applied by the base conventions and computes `project.version` from Git tags, so a
> consuming project must not declare `version` in `gradle.properties` or the build script, and it needs a Git
> repository with at least one commit. `versioning.stage` (one of `alpha`, `beta`, `rc`, `release`) is optional,
> it is `beta` while the version line is below `1.0.0` and `release` from there on. Set it as a Gradle property
> rather than through the `versioning { }` block when the project applies `java-gradle-plugin`.

### Extraction

Gradle reads `checkstyle.xml`, `formatj.toml`, `cliff.toml`, the selected `LICENSE`, and its `HEADER` from the plugin jar.
Git, editors and git-cliff still need files on disk.
`checkLicense` also reads `LICENSE` from the project directory or a parent directory, and every Java file has to start with `HEADER`.
`extractConventions` writes them into the root project directory. In a multi-project build every project applying the
base or style conventions registers its own copy of the task, all writing the same root directory:

- `LICENSE`, `HEADER`
- `checkstyle.xml`, `formatj.toml`, `cliff.toml`
- `.editorconfig`, `.gitattributes`
- `.gitignore` (replaces the `# >>> cleanroom-conventions` region, keeps anything outside it, and fails if the region is opened but never closed)

It is a manual task. Hook it from a project-specific setup task if you want it on a known name:

```groovy filename="build.gradle"
tasks.register('setup') {
    dependsOn 'extractConventions'
}
```

### Settings Conventions

Applied from `settings.gradle`.

- Maven Central, the Gradle Plugin Portal, and [Cleanroom Maven](https://maven.cleanroommc.com) are injected before the project buildscript runs, so a later `repositories { }` only appends.
  - `com.cleanroommc`, `top.outlands`, `zone.rong`, `net.minecraftforge`, `de.oceanlabs.mcp` resolve from Cleanroom Maven.
- Foojay toolchain resolver, only when `conventions.provisionJava = true`.

### Base Conventions

- Applies `com.cleanroommc.versioning` gradle plugin, pinned at 3.2.0.
  - Configures projects to follow Cleanroom's Versioning Conventions.
  - `project.version` comes from the Git tags, `./gradlew -q printVersion` prints it.
- Default `group` is `com.cleanroommc` when the project has not set one.
- Force UTF-8 encoding on ALL `JavaCompile`, `Javadoc` and `Test` tasks.
- Mutes Javadoc's `missing` warnings, everything else in `-Xdoclint` stays on.
- Java toolchain from `conventions.javaMajor`.
- IDEA module downloads sources and Javadoc.
- Jar manifest `Implementation-*` and `Specification-*` match the POM identity (name, version, CleanroomMC).
- Verifiable rebuilding of artifacts
- Registers `extractConventions`. It is not attached to `build`, `check` or `assemble`.

### License Conventions

Set one license mode in `gradle.properties`:

| `conventions.license` value | License                                      | SPDX identifier  |
|-----------------------------|----------------------------------------------|------------------|
| `free`                      | MIT License                                  | `MIT`            |
| `open`                      | GNU Lesser General Public License version 3  | `LGPL-3.0-only`  |
| `visible`                   | CleanroomMC License Version 1.0              | Custom           |

`visible` is the default. The selected mode controls `checkLicense`, `extractConventions`, the Java header required by Checkstyle, and Maven POM license metadata. The license conventions apply `lifecycle-base`, so `checkLicense` is attached to `check` even without the `java` plugin, and it accepts a matching `LICENSE` in the project directory or a parent directory.

`conventions.beginFrom` optionally sets the first copyright year. The generated notice uses only the current year when it is unset and no existing notice is present. With an earlier starting year, it uses `StartingYear-CurrentYear`, for example `2021-2026`. When the year changes, `extractConventions` reads the starting year already stored in `HEADER` or `LICENSE`, preserves it, and advances the ending year. An explicit `beginFrom` value takes precedence. In `open` mode only `HEADER` carries the year: the LGPL license body is the unmodified FSF text and holds no project copyright line. Year preservation only matches `CleanroomMC contributors` notices; renaming the holder starts a new range from the current year.

### Style Conventions

- Applies [ClearSkies](https://github.com/Rongmario/ClearSkies), which expands star imports. No configuration.
- Applies [FormatJ](https://github.com/Rongmario/FormatJ) with `formatj.toml`.
- Applies Checkstyle with `checkstyle.xml`.

The three run in a fixed order, since each one judges what the previous one wrote:
- ClearSkies > FormatJ > Checkstyle

Checkstyle requires the selected license header from `HEADER` as a Java block comment at the top of every `.java` file.
It is matched line by line as a regular expression, with the copyright year left as a pattern, so a new year never
invalidates the header already written into every source file.

The `checkstyle.xml` on disk holds an `@LICENSE_HEADER@` placeholder rather than a
usable header. The plugin generates the resolved configuration at
`build/conventions/checkstyle.xml`; point IDE Checkstyle integrations at the generated file.

Checkstyle warns when an imported `Nullable`, `NonNull`, `Nonnull`, `NotNull` or `CheckForNull` annotation does not come from `org.jspecify.annotations`. The advisory stays at import level so legacy or generated fully-qualified references do not block a build.

> [!NOTE]
> FormatJ ships an IntelliJ plugin that reads `formatj.toml`.
>
> Run `extractConventions` (or copy [this file](formatj.toml) to the project root) and use the plugin to perform native formatting.

### Annotations Conventions

Added as `compileOnly` on every source set, so none of them reach a consumer's runtime classpath:

- `org.jspecify:jspecify` for nullness
- `org.jetbrains:annotations`
- `com.cleanroommc:anone`

### Testing Conventions

- `org.junit:junit-bom`, `junit-jupiter` and `junit-platform-launcher`.
- `mockito-core` and `mockito-junit-jupiter`.
- `org.assertj:assertj-bom`, `assertj-core` and `assertj-guava`.
- `useJUnitPlatform()` on every `Test`.
- Test logging prints passed, skipped and failed, with full exception traces.

### Benchmarking Conventions

Disabled by default. Enable it through the aggregate plugin in `gradle.properties`:

```properties filename="gradle.properties"
conventions.benchmarking = true
```

You can also apply `com.cleanroommc.conventions.benchmarking` directly. It creates an isolated `benchmark` source set rooted at `src/benchmark/java` and `src/benchmark/resources`, with OpenJDK JMH on its implementation and annotation processor classpaths. Its compile and runtime classpaths include `main` output and dependencies. It does not inherit from `test` or run as part of `test`, `check` or `build`.

Put JMH benchmarks under `src/benchmark/java` and add any benchmark-only libraries to `benchmarkImplementation`:

```groovy filename="build.gradle"
dependencies {
    benchmarkImplementation 'org.example:benchmark-fixtures:1.0.0'
}
```

Run all benchmarks:

```shell
./gradlew benchmark
```

Pass standard JMH arguments through the `JavaExec` task. For example:

```shell
./gradlew benchmark --args='MyBenchmark -wi 3 -i 5 -f 2'
```

### Publishing Conventions

- Adds the `Cleanroom` Maven repository (`https://maven.cleanroommc.com`)
  - Authenticate with `CleanroomUsername` and `CleanroomPassword`.
- Adds a `sources` jar and a `javadoc` jar.
- Fills in POM defaults on every Maven publication:
  - Name
  - Description
  - Url
  - CleanroomMC organization
  - Selected license name and URL
  - `scm` connections.
    - Repository URL comes from `conventions.repositoryUrl`, then the git upstream remote
    - `gradlePlugin.vcsUrl` for plugin projects.
- Creates a `maven` publication from the `java` component (if there is no existing `maven` publication)
  - Unless the project applies `java-gradle-plugin` (which brings its own `pluginMaven` publication)
- For `java-gradle-plugin` projects: applies `com.gradle.plugin-publish`.
- Signs every Maven publication when both `signingKey` and `signingPassword` are set.
  - If either property is missing, signing is left off and publish tasks still run.

- Nothing here writes `cliff.toml`. Only the `git-cliff` CLI reads it, so the release workflow fetches it from this repository at the ref the workflow was called at, unless the project ships its own.

### Mod Publishing

Disabled by default. Enable it in the project's `gradle.properties`:

```properties filename="gradle.properties"
conventions.modPublishing = true
```

This exposes the [mod-publish-plugin's](https://modmuss50.github.io/mod-publish-plugin/) `publishMods` extension. Add either or both distributions:

> [!NOTE]
> The following properties have been applied:
> - Minecraft Version: "1.12.2"
> - Mod Loader: "forge" ("cleanroom" if/when distributions support it)
> - File: output of the "jar" task
> - Max Retries: 5
> - Version: `project.version`
> - Version Type: (Alpha/Beta/Stable) applied via Cleanroom's Versioning module
> - CurseForge Access Token: `CURSEFORGE_TOKEN` environment variable
> - Modrinth Access Token: `MODRINTH_TOKEN` environment variable

> [!TIP]
> Any value can be overridden through the upstream DSL. A distribution is only configured when it is named here. Check out the [plugin's wiki](https://modmuss50.github.io/mod-publish-plugin/)

```groovy filename="build.gradle"
conventions {
    mods {
        curseforge = '123456'
        modrinth = 'abcdef'

        // curseforge { }
        // modrinth { }
        // To configure the upstream DSLs
    }
}
```

## Branches

| Branch                    | Purpose                                        | Version                      | CI                                 |
|---------------------------|------------------------------------------------|------------------------------|------------------------------------|
| `master`                  | The release line. Every tag is cut here        | `1.1.2-dev.3+run.24`         | Builds on push, publishes on a tag |
| `develop/<major>.<minor>` | A development line for the version it leads to | `1.4.0-dev.7+run.26`         | Builds on push                     |
| `feature/<slug>`          | New work, merged back through a pull request   | `1.4.0-feature-foo.3+run.28` | Builds on its pull request         |
| `fix/<slug>`              | A fix, merged back through a pull request      | `1.1.2-fix-crash.1+run.29`   | Builds on its pull request         |

The branch prefix matches the commit type the work carries, so a `feature/` branch lands `feat` commits and a `fix/` branch lands `fix` commits, the two types `cliff.toml` puts at the top of a changelog. Any other prefix behaves exactly like those two, so `docs/`, `refactor/` and `chore/` need no extra setup.

`feature/` and `fix/` branches take their number from the line they were cut from and count their commits under their own label, so a branch cut from `develop/1.4` reads `1.4.0-feature-foo.3` and the same branch cut from `master` reads `1.1.2-feature-foo.3`. Only tags on `master` publish. Nothing built from a working branch or a development branch reaches a repository unless a workflow is written to do it.

The version column describes [Cleanroom Versioning](https://github.com/CleanroomMC/CleanroomVersioning) 3.x, which the conventions plugin pins at 3.2.0.

Delete a working branch once it is merged. Delete or rename a development branch once its version is tagged, since a development branch that has been released fails the build by design.

## GitHub Actions

Reusable workflows live in this repository. Pin the `@` ref to a tag (or a commit), which can be better than pinning to `@master` which tracks whatever is latest.

This repository's own wrappers are [`.github/workflows/ci.yml`](.github/workflows/ci.yml) and [`.github/workflows/publish.yml`](.github/workflows/publish.yml). Other CleanroomMC projects should call the reusable files below.

### Build

[`.github/workflows/build.yml`](.github/workflows/build.yml) compiles, tests and uploads `**/build/libs`.

```yaml filename=".github/workflows/ci.yml"
name: CI

on:
  push:
    branches:
      - master
      - 'develop/**'
  pull_request:
    types: [opened, synchronize, reopened, ready_for_review, review_requested]
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    uses: CleanroomMC/Conventions/.github/workflows/build.yml@master
    # with:
    #   working-directory: gradle-plugin
    #   java-version: '25'
```

| Input                | Default         | Purpose                                                                |
|----------------------|-----------------|------------------------------------------------------------------------|
| `working-directory`  | `.`             | Directory that contains `gradlew`, or a unique wrapper two levels down |
| `artifact-path`      | `**/build/libs` | Paths uploaded after a successful build                                |
| `if-no-files-found`  | `warn`          | `warn`, `error` or `ignore` when nothing matches                       |
| `java-version`       | `25`            | Temurin JDK used to launch Gradle                                      |
| `release-branch`     | `master`        | Branch merges are skipped on, matching `versioning.releaseBranch`      |
| `timeout-minutes`    | `15`            | Job timeout                                                            |
| `cache-provider`     | `enhanced`      | `basic` (MIT) or `enhanced` (Gradle Terms of Use)                      |
| `build-scan-publish` | `true`          | Publish build scans to `scans.gradle.com`                              |

The workflow runs `./gradlew build`. It checks out the full history and tags, which Versioning reads, and the run number reaches the version through the Actions environment. Draft PRs are skipped until they are marked ready for review.

The push filter keeps `feature/` and `fix/` branches from building twice for one commit, once for the push and once for the pull request. Drop it to build every pushed branch, at the cost of that duplicate run.

A guard job skips a merge commit pushed to `release-branch`. It sits between the merge and the release tag, so it computes the next patch of the previous tag, a number that will never ship. Merges into a development branch still build, since that branch pins its own number.

### Release

[`.github/workflows/release.yml`](.github/workflows/release.yml) builds the tag, generates release notes and `CHANGELOG.md` through git-cliff, then optionally publishes. For tags on the default branch, the generated `CHANGELOG.md` is committed there after the release.

```yaml filename=".github/workflows/publish.yml"
name: Publish

on:
  push:
    tags:
      - '[0-9]+.[0-9]+.[0-9]+'
  workflow_dispatch:

permissions:
  contents: write
  issues: read
  pull-requests: read

jobs:
  release:
    uses: CleanroomMC/Conventions/.github/workflows/release.yml@master
    with:
      publish-maven: true
      publish-plugin-portal: false
      publish-mods: false
    secrets: inherit
```

A library typically enables `publish-maven`. A Gradle plugin also sets `publish-plugin-portal: true`. A mod sets `conventions.modPublishing = true` in Gradle and `publish-mods: true` here.

| Input                   | Default               | Purpose                                                                   |
|-------------------------|-----------------------|---------------------------------------------------------------------------|
| `working-directory`     | `.`                   | Directory that contains `gradlew`                                         |
| `artifact-path`         | `**/build/libs/*.jar` | Jars attached to the GitHub Release                                       |
| `cliff-config`          | `cliff.toml`          | Local override. If missing, the workflow fetches this repo's `cliff.toml` |
| `java-version`          | `25`                  | Temurin JDK used to launch Gradle                                         |
| `timeout-minutes`       | `30`                  | Job timeout                                                               |
| `cache-provider`        | `basic`               | Gradle User Home cache                                                    |
| `publish-maven`         | `true`                | `publishAllPublicationsToCleanroomRepository`                             |
| `publish-plugin-portal` | `false`               | `publishPlugins`                                                          |
| `publish-mods`          | `false`               | `publishMods` (CurseForge / Modrinth)                                     |

| Secret                  | Used when               |
|-------------------------|-------------------------|
| `MAVEN_NAME`            | `publish-maven`         |
| `MAVEN_PASSWORD`        | `publish-maven`         |
| `GRADLE_PUBLISH_KEY`    | `publish-plugin-portal` |
| `GRADLE_PUBLISH_SECRET` | `publish-plugin-portal` |
| `SIGNING_KEY`           | `publish-plugin-portal` |
| `SIGNING_PASSWORD`      | `publish-plugin-portal` |
| `CURSEFORGE_TOKEN`      | `publish-mods`          |
| `MODRINTH_TOKEN`        | `publish-mods`          |

`publish-mods` needs at least one of the two store tokens. A tag always creates a GitHub Release with the matched jars, even when every publish input is false. The generated `CHANGELOG.md` is committed to the default branch when the tag is on that branch.

git-cliff uses the first of: the path in `cliff-config`, that file at the repository root, then `cliff.toml` from this Conventions ref.
