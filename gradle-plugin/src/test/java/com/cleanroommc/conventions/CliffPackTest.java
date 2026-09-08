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

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.cleanroommc.conventions.CliffPipeline.CliffEntry;
import com.cleanroommc.conventions.CliffPipeline.Parser;
import com.cleanroommc.conventions.CliffPipeline.Preprocessor;

class CliffPackTest {

    private static CliffPipeline pipeline;

    @BeforeAll
    static void loadPipeline() {
        pipeline = CliffPipeline.load();
    }

    @Test
    void gitSectionMatchesCliffToml() {
        assertThat(pipeline.splitCommits()).isTrue();
        assertThat(pipeline.processingOrder()).containsExactly(
                "commit_preprocessors",
                "split_commits",
                "conventional_commits",
                "commit_parsers",
                "link_parsers"
        );

        List<Preprocessor> preprocessors = pipeline.preprocessors();
        assertThat(preprocessors).hasSize(3);
        assertThat(preprocessors.get(0).pattern().pattern()).isEqualTo("\\s*\\((\\w+\\s)?#([0-9]+)\\)");
        assertThat(preprocessors.get(0).replace()).isEmpty();
        assertThat(preprocessors.get(1).pattern().pattern()).isEqualTo("(?m)^[ \\t]*[-*][ \\t]+");
        assertThat(preprocessors.get(1).replace()).isEmpty();
        assertThat(preprocessors.get(2).pattern().pattern()).isEqualTo("(?s)^.*\\n");
        assertThat(preprocessors.get(2).replaceCommand()).isEqualTo(CliffPipeline.COLLAPSE_COMMAND);

        List<Parser> parsers = pipeline.parsers();
        assertThat(parsers).hasSize(17);
        assertParser(parsers.get(0), "^pack(?:\\(|:|!)", "<!-- 0 -->Comprehensive", false);
        assertParser(parsers.get(1), "^feat", "<!-- 1 -->Feature", false);
        assertParser(parsers.get(2), "^fix", "<!-- 2 -->Bug Fix", false);
        assertParser(parsers.get(3), "^perf", "<!-- 3 -->Performance", false);
        assertParser(parsers.get(4), "^refactor", "<!-- 4 -->Refactor", false);
        assertParser(parsers.get(5), "^doc", "<!-- 5 -->Documentation", false);
        assertParser(parsers.get(6), "^test", "<!-- 6 -->Testing", false);
        assertParser(parsers.get(7), "^build", "<!-- 7 -->Build and Dependencies", false);
        assertParser(parsers.get(8), "^chore\\(deps\\)", "<!-- 7 -->Build and Dependencies", false);
        assertParser(parsers.get(9), "^ci", "<!-- 8 -->CI", false);
        assertParser(parsers.get(10), "^chore", null, true);
        assertParser(parsers.get(11), "^style", null, true);
        assertParser(parsers.get(12), "^Merge", null, true);
        assertParser(parsers.get(13), "^revert", null, true);
        assertParser(parsers.get(14), "^Signed-off-by", null, true);
        assertParser(parsers.get(15), "^Co-authored-by", "<!-- 10 -->Co-authors", false);
        assertParser(parsers.get(16), ".*", "<!-- 9 -->Other", false);
    }

    @Test
    void packSplitsNestedConventionalCommits() {
        List<CliffEntry> entries = pipeline.process(
                """
                pack: implemented large surface PR (#123)

                - fix(inventory): shift-click from the hotbar
                - feat(inventory): add extra slots
                - chore: tweak comments
                Co-authored-by: Example <example@example.com>
                """
        );
        assertThat(entries).hasSize(4);
        assertEntry(entries.get(0), "Comprehensive", null, "implemented large surface PR");
        assertEntry(entries.get(1), "Bug Fix", "inventory", "shift-click from the hotbar");
        assertEntry(entries.get(2), "Feature", "inventory", "add extra slots");
        assertEntry(entries.get(3), "Co-authors", null, "Example <example@example.com>");
    }

    @Test
    void regularCommitsStayOneCollapsedEntry() {
        List<CliffEntry> entries = pipeline.process(
                """
                feat(example): example multiline commit (#389)

                - feature(another): implemented another feature
                Fixes #12
                """
        );
        assertThat(entries).hasSize(1);
        assertEntry(entries.getFirst(), "Feature", "example", "example multiline commit");
        assertThat(entries.getFirst().message()).contains("Fixes #12");
        assertThat(entries.getFirst().message()).doesNotContain("\n");
    }

    @Test
    void packageIsNotAPackCommit() {
        List<CliffEntry> entries = pipeline.process(
                """
                package: bump the wrapper

                - feat(core): should not split
                """
        );
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().group()).isEqualTo("Other");
        assertThat(entries.getFirst().message()).doesNotContain("\n");
    }

    private static void assertEntry(CliffEntry entry, String group, String scope, String description) {
        assertThat(entry.group()).isEqualTo(group);
        assertThat(entry.scope()).isEqualTo(scope);
        assertThat(entry.description()).isEqualTo(description);
    }

    private static void assertParser(Parser parser, String pattern, String group, boolean skip) {
        assertThat(parser.message().pattern()).isEqualTo(pattern);
        assertThat(parser.group()).isEqualTo(group);
        assertThat(parser.skip()).isEqualTo(skip);
    }

}
