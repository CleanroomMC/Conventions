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
import java.time.Year;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

final class LicenseYears {

    static final String YEAR_TOKEN = "@YEAR@";

    private static final Pattern COPYRIGHT = Pattern.compile("(?m)^(Copyright (?:\\(c\\)|©) )(\\d{4})(?:-(?:\\d{4}|present))?( CleanroomMC contributors)$");

    private final int begin;
    private final int current;

    private LicenseYears(int begin, int current) {
        if (begin < 1000) {
            throw new GradleException("conventions.beginFrom must be a four-digit year.");
        }
        if (begin > current) {
            throw new GradleException("conventions.beginFrom must not be later than the current year " + current + ".");
        }
        this.begin = begin;
        this.current = current;
    }

    static Provider<LicenseYears> provider(Project project) {
        return provider(project, project);
    }

    static Provider<LicenseYears> provider(Project project, Project persistedProject) {
        ConventionsExtension conventions = ConventionsExtension.register(project);
        Provider<Integer> begin = conventions.getBeginFrom().orElse(beginProvider(persistedProject));
        return currentYear(project.getProviders()).zip(begin, (currentYear, firstYear) -> new LicenseYears(firstYear, currentYear));
    }

    static Provider<Integer> beginProvider(Project project) {
        ProviderFactory providers = project.getProviders();
        Provider<Integer> persisted = providers.of(
                PersistedBeginYear.class,
                source -> source.getParameters().getStartDirectory().set(project.getLayout().getProjectDirectory())
        );
        return persisted.orElse(currentYear(providers));
    }

    static LicenseYears of(int begin, int current) {
        return new LicenseYears(begin, current);
    }

    static LicenseYears current() {
        int current = Year.now().getValue();
        return new LicenseYears(current, current);
    }

    String value() {
        return begin == current ? Integer.toString(current) : begin + "-" + current;
    }

    String apply(String text) {
        return text.replace(YEAR_TOKEN, value());
    }

    private static Provider<Integer> currentYear(ProviderFactory providers) {
        return providers.of(CurrentYear.class, _ -> {});
    }

    /**
     * The current year is external state, so it goes through a value source. Reading it directly would freeze the year
     * into the configuration cache entry.
     */
    public abstract static class CurrentYear implements ValueSource<Integer, ValueSourceParameters.None> {

        @Override
        public Integer obtain() {
            return Year.now().getValue();
        }

    }

    /**
     * Walks parent directories for an existing copyright notice, so an established starting year survives a new year.
     * Its depth is not known up front, so a value source carries the reads past the configuration cache.
     */
    public abstract static class PersistedBeginYear implements ValueSource<Integer, PersistedBeginYear.Parameters> {

        public interface Parameters extends ValueSourceParameters {

            DirectoryProperty getStartDirectory();

        }

        @Override
        public Integer obtain() {
            Path cursor = getParameters().getStartDirectory().getAsFile().get().toPath().toAbsolutePath().normalize();
            while (cursor != null) {
                for (String fileName : new String[] { "HEADER", "LICENSE" }) {
                    Integer year = read(cursor.resolve(fileName));
                    if (year != null) {
                        return year;
                    }
                }
                cursor = cursor.getParent();
            }
            return null;
        }

        // The walk reaches directories this build does not own, so an unreadable or non-UTF-8 candidate is not ours.
        private static Integer read(Path file) {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            String text;
            try {
                text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return null;
            }
            Matcher matcher = COPYRIGHT.matcher(text);
            return matcher.find() ? Integer.parseInt(matcher.group(2)) : null;
        }

    }

}
