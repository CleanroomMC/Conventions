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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConventionsFileTest {

    @Test
    void javaHeaderWrapsTheHeaderFile() {
        int current = Year.now().getValue();
        assertThat(LicenseMode.VISIBLE.javaHeader(LicenseYears.current())).isEqualTo(
            """
                /*
                 * Copyright (c) %d CleanroomMC contributors
                 *
                 * This file is licensed under the CleanroomMC License Version 1.0.
                 * See the applicable LICENSE file in this directory or a parent directory
                 * for the full licence terms.
                 *
                 * This is visible-source software and is not open-source software.
                 */""".formatted(
                current
            )
        );
    }

    @Test
    void checkstyleContainsTheLicenseHeaderPlaceholder() {
        assertThat(ConventionsFile.CHECKSTYLE.read()).contains("<property name=\"header\" value=\"@LICENSE_HEADER@\"/>");
    }

    @ParameterizedTest
    @EnumSource(LicenseMode.class)
    void checkstyleRendersEveryLicenseHeader(LicenseMode license) {
        assertThat(ConventionsFile.checkstyle(license)).doesNotContain("@LICENSE_HEADER@");
        assertThat(ConventionsFile.checkstyle(license)).contains(license.javaHeaderPattern().lines().findFirst().orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(LicenseMode.class)
    void theHeaderPatternMatchesEveryYearForm(LicenseMode license) {
        List<String> patterns = license.javaHeaderPattern().lines().toList();
        for (int begin = 2021; begin <= 2031; begin++) {
            List<String> header = license.javaHeader(LicenseYears.of(2021, begin)).lines().toList();
            assertThat(header).hasSameSizeAs(patterns);
            for (int line = 0; line < header.size(); line++) {
                assertThat(header.get(line)).matches(patterns.get(line));
            }
        }
    }

}
