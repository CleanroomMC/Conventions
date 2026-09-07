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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CliffPipeline {

    static final String COLLAPSE_COMMAND = "awk 'NR==1 && /^pack[(!:]/ {pack=1} {printf \"%s%s\", " + "(NR==1 ? \"\" : (pack || tolower($0) ~ /^co-authored-by:/ ? \"\\n\" : \"\\f\")), $0}' ";

    private static final Pattern CONVENTIONAL_HEADER = Pattern.compile("^(?<type>[^\\s(:!]+)(?:\\((?<scope>[^)]*)\\))?(?<breaking>!)?:\\s*(?<desc>.*)$");
    private static final Pattern PACK_HEADER = Pattern.compile("^pack[(!:]");
    private static final Pattern TOML_STRING = Pattern.compile("(?:'([^']*)'|\"((?:\\\\.|[^\"])*)\")");
    private static final Pattern GROUP_PREFIX = Pattern.compile("^<!--\\s*\\d+\\s*-->");

    private final boolean splitCommits;
    private final List<String> processingOrder;
    private final List<Preprocessor> preprocessors;
    private final List<Parser> parsers;

    private CliffPipeline(boolean splitCommits, List<String> processingOrder, List<Preprocessor> preprocessors, List<Parser> parsers) {
        this.splitCommits = splitCommits;
        this.processingOrder = List.copyOf(processingOrder);
        this.preprocessors = List.copyOf(preprocessors);
        this.parsers = List.copyOf(parsers);
    }

    static CliffPipeline load() {
        return parse(ConventionsFile.CLIFF.read());
    }

    static CliffPipeline parse(String toml) {
        String git = section(toml, "git");
        return new CliffPipeline(
                booleanValue(git, "split_commits"),
                stringArray(git, "processing_order"),
                parsePreprocessors(bracketArray(git, "commit_preprocessors")),
                parseParsers(bracketArray(git, "commit_parsers"))
        );
    }

    boolean splitCommits() {
        return splitCommits;
    }

    List<String> processingOrder() {
        return processingOrder;
    }

    List<Preprocessor> preprocessors() {
        return preprocessors;
    }

    List<Parser> parsers() {
        return parsers;
    }

    List<CliffEntry> process(String message) {
        String rendered = message;
        for (Preprocessor preprocessor : preprocessors) {
            rendered = preprocessor.apply(rendered);
        }
        List<String> pieces = new ArrayList<>();
        if (splitCommits) {
            for (String line : rendered.split("\n", -1)) {
                if (!line.isEmpty()) {
                    pieces.add(line);
                }
            }
        } else {
            pieces.add(rendered);
        }
        List<CliffEntry> entries = new ArrayList<>();
        for (String piece : pieces) {
            Parser parser = match(piece);
            if (parser == null || parser.skip) {
                continue;
            }
            Matcher header = CONVENTIONAL_HEADER.matcher(header(piece));
            String scope = null;
            String description = header(piece);
            if (header.matches()) {
                scope = header.group("scope");
                description = header.group("desc");
            }
            entries.add(new CliffEntry(displayGroup(parser.group), scope, description, piece));
        }
        return entries;
    }

    private Parser match(String message) {
        String trimmed = message.trim();
        for (Parser parser : parsers) {
            if (parser.message.matcher(trimmed).find()) {
                return parser;
            }
        }
        return null;
    }

    private static String header(String message) {
        int end = message.length();
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '\n' || c == '\r' || c == '\f') {
                end = i;
                break;
            }
        }
        return message.substring(0, end).trim();
    }

    private static String displayGroup(String group) {
        if (group == null) {
            return "";
        }
        return GROUP_PREFIX.matcher(group).replaceFirst("").trim();
    }

    private static String section(String toml, String name) {
        Pattern header = Pattern.compile("(?m)^\\[" + Pattern.quote(name) + "]\\s*$");
        Matcher matcher = header.matcher(toml);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing [" + name + "] in cliff.toml");
        }
        int start = matcher.end();
        Matcher next = Pattern.compile("(?m)^\\[").matcher(toml);
        int end = toml.length();
        if (next.find(start)) {
            end = next.start();
        }
        return toml.substring(start, end);
    }

    private static boolean booleanValue(String section, String key) {
        Matcher matcher = Pattern.compile("(?m)^" + Pattern.quote(key) + "\\s*=\\s*(true|false)\\s*$").matcher(section);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing " + key + " in [git]");
        }
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static List<String> stringArray(String section, String key) {
        String body = bracketArray(section, key);
        List<String> values = new ArrayList<>();
        Matcher matcher = TOML_STRING.matcher(body);
        while (matcher.find()) {
            values.add(unescape(matcher));
        }
        return values;
    }

    private static String bracketArray(String section, String key) {
        Matcher matcher = Pattern.compile(Pattern.quote(key) + "\\s*=\\s*\\[").matcher(section);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing " + key + " array in [git]");
        }
        return sliceBalanced(section, matcher.end() - 1, '[', ']');
    }

    private static List<Preprocessor> parsePreprocessors(String arrayBody) {
        List<Preprocessor> preprocessors = new ArrayList<>();
        for (String object : objects(arrayBody)) {
            String pattern = requiredString(object, "pattern");
            String replace = optionalString(object, "replace");
            String replaceCommand = optionalString(object, "replace_command");
            preprocessors.add(new Preprocessor(Pattern.compile(pattern), replace, replaceCommand));
        }
        return preprocessors;
    }

    private static List<Parser> parseParsers(String arrayBody) {
        List<Parser> parsers = new ArrayList<>();
        for (String object : objects(arrayBody)) {
            String message = requiredString(object, "message");
            String group = optionalString(object, "group");
            boolean skip = object.contains("skip = true");
            parsers.add(new Parser(Pattern.compile(message), group, skip));
        }
        return parsers;
    }

    private static List<String> objects(String arrayBody) {
        List<String> objects = new ArrayList<>();
        for (int i = 0; i < arrayBody.length(); i++) {
            i = skipString(arrayBody, i);
            if (i >= arrayBody.length()) {
                break;
            }
            if (arrayBody.charAt(i) == '{') {
                String object = '{' + sliceBalanced(arrayBody, i, '{', '}') + '}';
                objects.add(object);
                i += object.length() - 1;
            }
        }
        return objects;
    }

    private static String sliceBalanced(String text, int openIndex, char open, char close) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            i = skipString(text, i);
            if (i >= text.length()) {
                break;
            }
            char c = text.charAt(i);
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return text.substring(openIndex + 1, i);
                }
            }
        }
        throw new IllegalArgumentException("Unbalanced " + open + close + " in [git]");
    }

    private static int skipString(String text, int index) {
        if (index >= text.length()) {
            return index;
        }
        if (text.startsWith("'''", index) || text.startsWith("\"\"\"", index)) {
            String delimiter = text.substring(index, index + 3);
            int end = text.indexOf(delimiter, index + 3);
            return end < 0 ? text.length() : end + 2;
        }
        char quote = text.charAt(index);
        if (quote != '\'' && quote != '"') {
            return index;
        }
        for (int i = index + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote == '"' && c == '\\' && i + 1 < text.length()) {
                i++;
                continue;
            }
            if (c == quote) {
                return i;
            }
        }
        return text.length();
    }

    private static String requiredString(String object, String key) {
        String value = optionalString(object, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing " + key + " in " + object);
        }
        return value;
    }

    private static String optionalString(String object, String key) {
        Matcher matcher = Pattern.compile(Pattern.quote(key) + "\\s*=\\s*").matcher(object);
        if (!matcher.find()) {
            return null;
        }
        if (object.startsWith("'''", matcher.end()) || object.startsWith("\"\"\"", matcher.end())) {
            String delimiter = object.substring(matcher.end(), matcher.end() + 3);
            int end = object.indexOf(delimiter, matcher.end() + 3);
            if (end < 0) {
                return null;
            }
            return delimiter.charAt(0) == '\'' ? object.substring(matcher.end() + 3, end) : unescape(object.substring(matcher.end() + 3, end));
        }
        Matcher quoted = TOML_STRING.matcher(object);
        if (!quoted.find(matcher.end()) || quoted.start() != matcher.end()) {
            return null;
        }
        return unescape(quoted);
    }

    private static String unescape(Matcher matcher) {
        if (matcher.group(1) != null) {
            return matcher.group(1);
        }
        return unescape(matcher.group(2));
    }

    private static String unescape(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) {
                out.append(c);
                continue;
            }
            char next = raw.charAt(++i);
            out.append(switch (next) {
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case '"' -> '"';
                case '\\' -> '\\';
                default -> next;
            });
        }
        return out.toString();
    }

    record CliffEntry(String group, String scope, String description, String message) {}

    record Parser(Pattern message, String group, boolean skip) {}

    record Preprocessor(Pattern pattern, String replace, String replaceCommand) {

        String apply(String message) {
            if (replace != null) {
                return pattern.matcher(message).replaceAll(replace);
            }
            if (replaceCommand == null || !pattern.matcher(message).find()) {
                return message;
            }
            if (!COLLAPSE_COMMAND.trim().equals(replaceCommand.trim())) {
                throw new IllegalStateException("Unsupported replace_command: " + replaceCommand);
            }
            return collapse(message);
        }

        private static String collapse(String message) {
            List<String> lines = new ArrayList<>(Arrays.asList(message.split("\\n", -1)));
            if (lines.size() > 1 && lines.getLast().isEmpty()) {
                lines.removeLast();
            }
            if (lines.isEmpty()) {
                return "";
            }
            boolean pack = PACK_HEADER.matcher(lines.getFirst()).find();
            StringBuilder collapsed = new StringBuilder(message.length());
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) {
                    String line = lines.get(i);
                    collapsed.append(pack || line.toLowerCase(Locale.ROOT).startsWith("co-authored-by:") ? '\n' : '\f');
                }
                collapsed.append(lines.get(i));
            }
            return collapsed.toString();
        }

    }

}
