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

import org.gradle.api.GradleException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CopyrightNoticeTest {

    @Test
    void currentYearStaysSingular() {
        assertThat(CopyrightNotice.of(2026, 2026).value()).isEqualTo("2026");
    }

    @Test
    void earlierYearBecomesARange() {
        assertThat(CopyrightNotice.of(2021, 2026).value()).isEqualTo("2021-2026");
    }

    @Test
    void replacesOnlyTheTokens() {
        String text = "Copyright (c) @YEAR@ @AUTHOR@\nCopyright (C) <year> author\n";
        assertThat(CopyrightNotice.of(2021, 2026).apply(text)).isEqualTo(
            "Copyright (c) 2021-2026 CleanroomMC contributors\nCopyright (C) <year> author\n"
        );
    }

    @Test
    void rejectsAFutureStartYear() {
        assertThatThrownBy(() -> CopyrightNotice.of(2027, 2026))
            .isInstanceOf(GradleException.class)
            .hasMessageContaining("must not be later than the current year 2026");
    }

    @Test
    void rejectsANonFourDigitStartYear() {
        assertThatThrownBy(() -> CopyrightNotice.of(999, 2026))
            .isInstanceOf(GradleException.class)
            .hasMessageContaining("must be a four-digit year");
    }

}
