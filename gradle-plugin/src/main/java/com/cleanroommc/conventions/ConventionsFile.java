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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

enum ConventionsFile {

    FORMAT_J("formatj.toml"),
    CHECKSTYLE("checkstyle.xml"),
    CLIFF("cliff.toml"),
    EDITOR_CONFIG(".editorconfig", "editorconfig"),
    GIT_ATTRIBUTES(".gitattributes", "gitattributes"),
    GIT_IGNORE(".gitignore", "gitignore", true);

    private static final String RESOURCE_DIRECTORY = "/resources/";
    private static final Pattern REGEX_METACHARACTER = Pattern.compile("[\\\\.\\[\\]{}()*+?^$|]");
    private static final String YEAR_PATTERN = "\\d{4}(?:-(?:\\d{4}|present))?";

    private final String fileName;
    private final String resourceName;
    private final boolean mergeMarkedRegion;

    ConventionsFile(String fileName) {
        this(fileName, fileName, false);
    }

    ConventionsFile(String fileName, String resourceName) {
        this(fileName, resourceName, false);
    }

    ConventionsFile(String fileName, String resourceName, boolean mergeMarkedRegion) {
        this.fileName = fileName;
        this.resourceName = resourceName;
        this.mergeMarkedRegion = mergeMarkedRegion;
    }

    String fileName() {
        return fileName;
    }

    boolean mergeMarkedRegion() {
        return mergeMarkedRegion;
    }

    static String checkstyle(LicenseMode license) {
        String header = license.javaHeaderPattern()
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "\\n");
        return CHECKSTYLE.read().replace("@LICENSE_HEADER@", header);
    }

    static String toJavaBlockComment(String text) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        while (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        StringBuilder comment = new StringBuilder("/*\n");
        if (!normalized.isEmpty()) {
            for (String line : normalized.split("\n", -1)) {
                if (line.isEmpty()) {
                    comment.append(" *\n");
                } else {
                    comment.append(" * ").append(line).append('\n');
                }
            }
        }
        return comment.append(" */").toString();
    }

    /**
     * Renders the header as one Checkstyle {@code RegexpHeader} line pattern per line. The copyright year stays a
     * pattern, so a new year never invalidates the header already written into every source file.
     */
    static String toJavaBlockCommentPattern(String template) {
        StringBuilder pattern = new StringBuilder();
        for (String line : toJavaBlockComment(template).split("\n", -1)) {
            if (!pattern.isEmpty()) {
                pattern.append('\n');
            }
            String quoted = REGEX_METACHARACTER.matcher(line).replaceAll("\\\\$0");
            pattern.append('^').append(quoted.replace(LicenseYears.YEAR_TOKEN, YEAR_PATTERN)).append('$');
        }
        return pattern.toString();
    }

    String read() {
        return readResource(resourceName);
    }

    static String readResource(String resourceName) {
        try (InputStream stream = ConventionsFile.class.getResourceAsStream(RESOURCE_DIRECTORY + resourceName)) {
            if (stream == null) {
                throw new GradleException("Convention resource " + resourceName + " is missing from the conventions plugin jar");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read convention resource " + resourceName + " from the conventions plugin jar", e);
        }
    }

    @Override
    public String toString() {
        return fileName;
    }

}
