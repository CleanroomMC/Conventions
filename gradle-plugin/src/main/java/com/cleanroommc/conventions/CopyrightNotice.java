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

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CopyrightNotice {

    static final String YEAR_TOKEN = "@YEAR@";
    static final String AUTHOR_TOKEN = "@AUTHOR@";

    private final int begin;
    private final int current;
    private final String author;

    private CopyrightNotice(int begin, int current, String author) {
        if (begin < 1000) {
            throw new GradleException("conventions.beginFrom must be a four-digit year.");
        }
        if (begin > current) {
            throw new GradleException("conventions.beginFrom must not be later than the current year " + current + ".");
        }
        this.begin = begin;
        this.current = current;
        this.author = author;
    }

    static Provider<CopyrightNotice> provider(Project project) {
        return provider(project, project);
    }

    static Provider<CopyrightNotice> provider(Project project, Project persistedProject) {
        ConventionsExtension conventions = ConventionsExtension.register(project);
        Provider<String> author = conventions.getAuthor();
        Provider<Integer> begin = conventions.getBeginFrom().orElse(beginProvider(persistedProject, author));
        Provider<Integer> current = currentYear(project.getProviders());
        return author.flatMap(holder -> current.zip(
            begin,
            (currentYear, firstYear) -> new CopyrightNotice(firstYear, currentYear, holder)
        ));
    }

    static Provider<Integer> beginProvider(Project project, Provider<String> author) {
        ProviderFactory providers = project.getProviders();
        Provider<Integer> persisted = providers.of(PersistedBeginYear.class, source -> {
            source.getParameters().getStartDirectory().set(project.getLayout().getProjectDirectory());
            source.getParameters().getAuthor().set(author);
        });
        return persisted.orElse(currentYear(providers));
    }

    static CopyrightNotice of(int begin, int current) {
        return new CopyrightNotice(begin, current, ConventionsDefaults.AUTHOR);
    }

    static CopyrightNotice current() {
        int current = Year.now().getValue();
        return of(current, current);
    }

    private static Provider<Integer> currentYear(ProviderFactory providers) {
        return providers.of(CurrentYear.class, _ -> { });
    }

    String value() {
        return begin == current ? Integer.toString(current) : begin + "-" + current;
    }

    String apply(String text) {
        return text.replace(YEAR_TOKEN, value()).replace(AUTHOR_TOKEN, author);
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

        // The walk reaches directories this build does not own, so an unreadable or non-UTF-8 candidate is not ours.
        private static Integer read(Path file, Pattern copyright) {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            String text;
            try {
                text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return null;
            }
            Matcher matcher = copyright.matcher(text);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
        }

        @Override
        public Integer obtain() {
            Pattern copyright = Pattern.compile(
                "(?m)^Copyright (?:\\(c\\)|©) (\\d{4})(?:-(?:\\d{4}|present))? " +
                    Pattern.quote(getParameters().getAuthor().get()) + "$"
            );
            Path cursor = getParameters().getStartDirectory().getAsFile().get().toPath().toAbsolutePath().normalize();
            while (cursor != null) {
                for (String fileName : new String[] { "HEADER", "LICENSE" }) {
                    Integer year = read(cursor.resolve(fileName), copyright);
                    if (year != null) {
                        return year;
                    }
                }
                cursor = cursor.getParent();
            }
            return null;
        }

        public interface Parameters extends ValueSourceParameters {

            DirectoryProperty getStartDirectory();

            Property<String> getAuthor();

        }

    }

}
