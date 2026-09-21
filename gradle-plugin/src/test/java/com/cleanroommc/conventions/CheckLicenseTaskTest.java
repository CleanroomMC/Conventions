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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CheckLicenseTaskTest {

    @TempDir
    Path dir;

    @Test
    void findLicenseWalksToAParent() throws IOException {
        Files.writeString(dir.resolve("LICENSE"), "root\n");
        Path nested = Files.createDirectories(dir.resolve("gradle-plugin"));
        assertThat(CheckLicenseTask.findLicense(nested)).isEqualTo(dir.resolve("LICENSE"));
    }

    @Test
    void findLicensePrefersTheNearestFile() throws IOException {
        Files.writeString(dir.resolve("LICENSE"), "root\n");
        Path nested = Files.createDirectories(dir.resolve("gradle-plugin"));
        Files.writeString(nested.resolve("LICENSE"), "nested\n");
        assertThat(CheckLicenseTask.findLicense(nested)).isEqualTo(nested.resolve("LICENSE"));
    }

    @Test
    void findLicenseReturnsNullWhenAbsent() {
        assertThat(CheckLicenseTask.findLicense(dir)).isNull();
    }

    @Test
    void sameLicenseIgnoresCarriageReturns() {
        assertThat(CheckLicenseTask.sameLicense("a\nb\n", "a\r\nb\r\n")).isTrue();
    }

}
