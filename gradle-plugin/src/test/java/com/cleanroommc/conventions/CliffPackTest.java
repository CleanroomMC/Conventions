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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Nested
    class Groups {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(
                delimiter = '|',
                textBlock = """
                        feat: add a flag                        | Feature                | -          | add a flag
                        feat(ui): add a flag                    | Feature                | ui         | add a flag
                        feat!: break the flag api               | Feature                | -          | break the flag api
                        feat(ui)!: break the flag api           | Feature                | ui         | break the flag api
                        feature: alias of feat                  | Feature                | -          | alias of feat
                        fix: handle null                        | Bug Fix                | -          | handle null
                        fix(parser): handle null                | Bug Fix                | parser     | handle null
                        perf: cache the lookup                  | Performance            | -          | cache the lookup
                        refactor: extract the parser            | Refactor               | -          | extract the parser
                        doc: describe the flag                  | Documentation          | -          | describe the flag
                        docs: describe the flag                 | Documentation          | -          | describe the flag
                        docs(readme): describe the flag         | Documentation          | readme     | describe the flag
                        test: cover the parser                  | Testing                | -          | cover the parser
                        tests: cover the parser                 | Testing                | -          | cover the parser
                        build: bump the wrapper                 | Build and Dependencies | -          | bump the wrapper
                        build(gradle): bump the wrapper         | Build and Dependencies | gradle     | bump the wrapper
                        chore(deps): bump junit                 | Build and Dependencies | deps       | bump junit
                        ci: cache gradle                        | CI                     | -          | cache gradle
                        ci(github): cache gradle                | CI                     | github     | cache gradle
                        pack: implemented large surface PR      | Comprehensive          | -          | implemented large surface PR
                        pack!: overhaul the public api          | Comprehensive          | -          | overhaul the public api
                        pack(inventory): overhaul the ui        | Comprehensive          | inventory  | overhaul the ui
                        pack(inventory)!: overhaul the ui       | Comprehensive          | inventory  | overhaul the ui
                        rewrite the world generator             | Other                  | -          | rewrite the world generator
                        Co-authored-by: Example <e@e.com>       | Co-authors             | -          | Example <e@e.com>
                        """
        )
        void mapsSubjectToGroup(String message, String group, String scope, String description) {
            List<CliffEntry> entries = pipeline.process(message);
            assertThat(entries).hasSize(1);
            assertEntry(entries.getFirst(), group, blankToNull(scope), description);
        }

    }

    @Nested
    class Skipped {

        @ParameterizedTest
        @ValueSource(
                strings = {
                        "chore: ignore me",
                        "chore(release): prepare for 1.0.0",
                        "chore(deps-dev): bump a test fixture",
                        "style: reformat",
                        "Merge branch 'main'",
                        "Merge pull request #1 from org/branch",
                        "revert: undo that change",
                        "Signed-off-by: Example <example@example.com>"
                }
        )
        void dropsNoiseTypes(String message) {
            assertThat(pipeline.process(message)).isEmpty();
        }

        @Test
        void dropsChoreEvenWhenItHasABody() {
            assertThat(pipeline.process("chore: ignore me\n\nSome body")).isEmpty();
        }

    }

    @Nested
    class Preprocessors {

        @Test
        void stripsBarePullRequestNumber() {
            List<CliffEntry> entries = pipeline.process("feat(args): add a flag (#389)");
            assertThat(entries).hasSize(1);
            assertEntry(entries.getFirst(), "Feature", "args", "add a flag");
        }

        @Test
        void stripsWordPrefixedPullRequestNumber() {
            List<CliffEntry> entries = pipeline.process("feat(args): add a flag (GH #389)");
            assertThat(entries).hasSize(1);
            assertEntry(entries.getFirst(), "Feature", "args", "add a flag");
        }

        @Test
        void leavesBareIssueReferences() {
            List<CliffEntry> entries = pipeline.process("fix(parser): handle empty input\n\nFixes #12");
            assertThat(entries).hasSize(1);
            assertThat(entries.getFirst().message()).contains("Fixes #12");
        }

        @Test
        void stripsLeadingBulletsBeforeCollapse() {
            List<CliffEntry> entries = pipeline.process(
                    """
                    feat(example): example multiline commit

                    - not a nested commit
                    * also not nested
                    """
            );
            assertThat(entries).hasSize(1);
            assertEntry(entries.getFirst(), "Feature", "example", "example multiline commit");
            assertThat(entries.getFirst().message()).doesNotContain("- not");
            assertThat(entries.getFirst().message()).doesNotContain("* also");
            assertThat(entries.getFirst().message()).contains("not a nested commit");
            assertThat(entries.getFirst().message()).contains("also not nested");
        }

        @Test
        void collapsesNonPackBodiesOntoOneLine() {
            List<CliffEntry> entries = pipeline.process("feat(ui): add a button\n\nA longer explanation.\n");
            assertThat(entries).hasSize(1);
            assertThat(entries.getFirst().message()).doesNotContain("\n");
            assertThat(entries.getFirst().message()).contains("\f");
        }

        @Test
        void doesNotCollapsePackBodies() {
            List<CliffEntry> entries = pipeline.process("pack: subject\n\nfeat(ui): add a button\n");
            assertThat(entries).hasSize(2);
            assertThat(entries.get(0).message()).contains("pack: subject");
            assertThat(entries.get(0).message()).doesNotContain("feat(ui)");
        }

    }

    @Nested
    class PackCommits {

        @Test
        void subjectLandsInComprehensiveAndNestedLinesKeepTheirGroups() {
            List<CliffEntry> entries = pipeline.process(
                    """
                    pack: implemented large surface PR (#123)

                    - fix(inventory): shift-click from the hotbar
                    - feat(inventory): add extra slots
                    - perf(inventory): avoid rebuilding the slot list
                    """
            );
            assertThat(entries).hasSize(4);
            assertEntry(entries.get(0), "Comprehensive", null, "implemented large surface PR");
            assertEntry(entries.get(1), "Bug Fix", "inventory", "shift-click from the hotbar");
            assertEntry(entries.get(2), "Feature", "inventory", "add extra slots");
            assertEntry(entries.get(3), "Performance", "inventory", "avoid rebuilding the slot list");
        }

        @Test
        void nestedLinesCoverEveryParserGroup() {
            List<CliffEntry> entries = pipeline.process(
                    """
                    pack: implemented large surface PR (#123)

                    - feat(inventory): add extra slots
                    - fix(inventory): shift-click from the hotbar
                    - perf(inventory): avoid rebuilding the slot list
                    - refactor(inventory): extract slot lookup
                    - docs(inventory): document the extra slots
                    - test(inventory): cover shift-click
                    - build: bump the wrapper
                    - chore(deps): bump junit
                    - ci: cache gradle
                    - chore: tweak comments
                    - style: reformat
                    - Merge branch 'tmp'
                    - revert: undo a draft
                    - Signed-off-by: Example <example@example.com>
                    - Co-authored-by: Example <example@example.com>
                    - rewrite the slot renderer
                    """
            );
            assertThat(entries).hasSize(12);
            assertEntry(entries.get(0), "Comprehensive", null, "implemented large surface PR");
            assertEntry(entries.get(1), "Feature", "inventory", "add extra slots");
            assertEntry(entries.get(2), "Bug Fix", "inventory", "shift-click from the hotbar");
            assertEntry(entries.get(3), "Performance", "inventory", "avoid rebuilding the slot list");
            assertEntry(entries.get(4), "Refactor", "inventory", "extract slot lookup");
            assertEntry(entries.get(5), "Documentation", "inventory", "document the extra slots");
            assertEntry(entries.get(6), "Testing", "inventory", "cover shift-click");
            assertEntry(entries.get(7), "Build and Dependencies", null, "bump the wrapper");
            assertEntry(entries.get(8), "Build and Dependencies", "deps", "bump junit");
            assertEntry(entries.get(9), "CI", null, "cache gradle");
            assertEntry(entries.get(10), "Co-authors", null, "Example <example@example.com>");
            assertEntry(entries.get(11), "Other", null, "rewrite the slot renderer");
        }

        @Test
        void scopedPackKeepsScopeOnTheComprehensiveLine() {
            List<CliffEntry> entries = pipeline.process(
                    """
                    pack(inventory): overhaul the inventory UI

                    * feat(inventory): add extra slots
                    """
            );
            assertThat(entries).hasSize(2);
            assertEntry(entries.get(0), "Comprehensive", "inventory", "overhaul the inventory UI");
            assertEntry(entries.get(1), "Feature", "inventory", "add extra slots");
        }

        @Test
        void breakingPackStillSplitsNestedLines() {
            List<CliffEntry> entries = pipeline.process(
                    """
                    pack!: overhaul the public api

                    - feat(api): add a new entrypoint
                    """
            );
            assertThat(entries).hasSize(2);
            assertEntry(entries.get(0), "Comprehensive", null, "overhaul the public api");
            assertEntry(entries.get(1), "Feature", "api", "add a new entrypoint");
        }

        @Test
        void nestedLinesWithoutBulletsStillSplit() {
            List<CliffEntry> entries = pipeline.process(
                    """
                    pack: implemented large surface PR

                    feat(ui): add a button
                    fix(ui): correct padding
                    """
            );
            assertThat(entries).hasSize(3);
            assertEntry(entries.get(0), "Comprehensive", null, "implemented large surface PR");
            assertEntry(entries.get(1), "Feature", "ui", "add a button");
            assertEntry(entries.get(2), "Bug Fix", "ui", "correct padding");
        }

        @Test
        void nestedPullRequestNumbersAreStripped() {
            List<CliffEntry> entries = pipeline.process(
                    """
                    pack: implemented large surface PR (#9)

                    - feat(ui): add a button (#10)
                    """
            );
            assertThat(entries).hasSize(2);
            assertEntry(entries.get(0), "Comprehensive", null, "implemented large surface PR");
            assertEntry(entries.get(1), "Feature", "ui", "add a button");
        }

        @Test
        void tabIndentedBulletsStillParse() {
            List<CliffEntry> entries = pipeline.process("pack: subject\n\n\t- feat(ui): add a button");
            assertThat(entries).hasSize(2);
            assertEntry(entries.get(1), "Feature", "ui", "add a button");
        }

        @Test
        void emptyLinesBetweenNestedChangesAreDropped() {
            List<CliffEntry> entries = pipeline.process(
                    """
                    pack: implemented large surface PR

                    - feat(ui): add a button

                    - fix(ui): correct padding
                    """
            );
            assertThat(entries).hasSize(3);
            assertEntry(entries.get(1), "Feature", "ui", "add a button");
            assertEntry(entries.get(2), "Bug Fix", "ui", "correct padding");
        }

        @Test
        void emptyPackIsOnlyTheComprehensiveLine() {
            List<CliffEntry> entries = pipeline.process("pack: implemented large surface PR");
            assertThat(entries).hasSize(1);
            assertEntry(entries.getFirst(), "Comprehensive", null, "implemented large surface PR");
        }

        @Test
        void skippedNestedLinesLeaveOnlyThePackSubject() {
            List<CliffEntry> entries = pipeline.process(
                    """
                    pack: implemented large surface PR

                    - chore: tweak comments
                    - style: reformat
                    """
            );
            assertThat(entries).hasSize(1);
            assertEntry(entries.getFirst(), "Comprehensive", null, "implemented large surface PR");
        }

        @Test
        void creditsCoAuthorsWithoutListingTrailersAsEntries() {
            List<CliffEntry> entries = pipeline.process(
                    """
                    pack: implemented large surface PR

                    - feat(ui): add a button
                    - chore: tweak comments
                    Co-authored-by: Example <example@example.com>
                    Signed-off-by: Example <example@example.com>
                    """
            );
            assertThat(entries).hasSize(3);
            assertEntry(entries.get(0), "Comprehensive", null, "implemented large surface PR");
            assertEntry(entries.get(1), "Feature", "ui", "add a button");
            assertEntry(entries.get(2), "Co-authors", null, "Example <example@example.com>");
        }

        @Test
        void doesNotTreatPackageAsPack() {
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

        @Test
        void featureAliasStillGroupsAsFeature() {
            List<CliffEntry> entries = pipeline.process(
                    """
                    pack: implemented large surface PR

                    - feature(world): generate structures
                    """
            );
            assertThat(entries).hasSize(2);
            assertEntry(entries.get(1), "Feature", "world", "generate structures");
        }

    }

    @Nested
    class RegularCommits {

        @Test
        void multilineBodyStaysOneEntry() {
            List<CliffEntry> entries = pipeline.process(
                    """
                    feat(example): example multiline commit

                    - feature(another): implemented another feature
                    This feature was done in this and that way so on and so forth

                    Commit-Footer...
                    """
            );
            assertThat(entries).hasSize(1);
            assertEntry(entries.getFirst(), "Feature", "example", "example multiline commit");
        }

        @Test
        void splitsCoAuthorsFromARegularCommit() {
            List<CliffEntry> entries = pipeline.process(
                    """
                    feat(ui): add a button

                    Co-authored-by: Example <example@example.com>
                    """
            );
            assertThat(entries).hasSize(2);
            assertEntry(entries.getFirst(), "Feature", "ui", "add a button");
            assertEntry(entries.get(1), "Co-authors", null, "Example <example@example.com>");
        }

        @Test
        void keepsSignedOffByOnARegularCommit() {
            List<CliffEntry> entries = pipeline.process(
                    """
                    feat(ui): add a button

                    Signed-off-by: Example <example@example.com>
                    """
            );
            assertThat(entries).hasSize(1);
            assertEntry(entries.getFirst(), "Feature", "ui", "add a button");
            assertThat(entries.getFirst().message()).contains("Signed-off-by: Example <example@example.com>");
        }

        @Test
        void keepsIssueFooterTextAfterCollapse() {
            List<CliffEntry> entries = pipeline.process(
                    """
                    fix(parser): handle empty input

                    Fixes #12
                    """
            );
            assertThat(entries).hasSize(1);
            assertEntry(entries.getFirst(), "Bug Fix", "parser", "handle empty input");
            assertThat(entries.getFirst().message()).contains("Fixes #12");
            assertThat(entries.getFirst().message()).doesNotContain("\n");
        }

        @Test
        void stripsPullRequestNumberFromTheSubject() {
            List<CliffEntry> entries = pipeline.process("feat(args): add a flag (#389)");
            assertThat(entries).hasSize(1);
            assertEntry(entries.getFirst(), "Feature", "args", "add a flag");
        }

        @Test
        void skipsChore() {
            assertThat(pipeline.process("chore: ignore me\n\nSome body")).isEmpty();
        }

        @Test
        void unconventionalSubjectGoesToOther() {
            List<CliffEntry> entries = pipeline.process("rewrite the world generator");
            assertThat(entries).hasSize(1);
            assertEntry(entries.getFirst(), "Other", null, "rewrite the world generator");
        }

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

    private static String blankToNull(String scope) {
        return "-".equals(scope) ? null : scope;
    }

}
