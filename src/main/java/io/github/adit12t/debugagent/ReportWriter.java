package io.github.adit12t.debugagent;

import io.github.adit12t.debugagent.Model.ErrorGroup;
import io.github.adit12t.debugagent.Model.Finding;
import io.github.adit12t.debugagent.Model.SourceSnippet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes the Markdown report and one regression test draft per finding. */
public class ReportWriter {

    public void write(List<Finding> findings, Path outDir) throws IOException {
        Path testsDir = outDir.resolve("regression-tests");
        Files.createDirectories(testsDir);

        StringBuilder md = new StringBuilder("# Debugging report\n\n");
        md.append(findings.size()).append(" distinct error(s) found in the logs.\n");

        int number = 1;
        for (Finding finding : findings) {
            ErrorGroup group = finding.group();
            md.append("\n## ").append(number++).append(". ").append(group.trace().simpleType());
            if (group.appFrame() != null) {
                md.append(" in `").append(group.appFrame().simpleClassName()).append('.')
                        .append(group.appFrame().method()).append("`");
            }
            md.append("\n\n");
            md.append("- **Occurrences:** ").append(group.count())
                    .append(" (first ").append(group.firstSeen()).append(", last ").append(group.lastSeen()).append(")\n");
            md.append("- **Service:** ").append(group.service()).append('\n');
            md.append("- **Logged as:** ").append(group.sampleMessage()).append('\n');
            md.append("- **Exception:** `").append(group.trace().exceptionType()).append(": ")
                    .append(group.trace().exceptionMessage()).append("`\n");

            SourceSnippet snippet = finding.snippet();
            if (snippet == null) {
                md.append("- **Location:** no application source found for this trace\n");
            } else {
                md.append("- **Location:** `").append(group.appFrame().file()).append("`, line ")
                        .append(snippet.line()).append("\n\n```java\n");
                for (int i = 0; i < snippet.lines().size(); i++) {
                    int lineNumber = snippet.startLine() + i;
                    md.append(lineNumber == snippet.line() ? ">> " : "   ")
                            .append(String.format("%3d", lineNumber)).append("  ")
                            .append(snippet.lines().get(i)).append('\n');
                }
                md.append("```\n");
            }

            md.append("\n**Root cause:** ").append(finding.diagnosis().rootCause()).append("\n\n");
            md.append("**Suggested fix:** ").append(finding.diagnosis().suggestedFix()).append("\n\n");

            if (finding.diagnosis().regressionTest() != null && group.appFrame() != null) {
                String method = group.appFrame().method();
                String fileName = group.appFrame().simpleClassName() + Character.toUpperCase(method.charAt(0))
                        + method.substring(1) + "RegressionTest.java";
                Files.writeString(testsDir.resolve(fileName), finding.diagnosis().regressionTest());
                md.append("**Regression test draft:** `regression-tests/").append(fileName).append("`\n\n");
            }

            md.append("**Verification:** ");
            if (!finding.verification().checked()) {
                md.append("not checked (no post-fix logs supplied)");
            } else if (finding.verification().resolved()) {
                md.append("RESOLVED. The error does not appear in the post-fix logs.");
            } else {
                md.append("STILL OCCURRING. Seen ").append(finding.verification().occurrencesAfter())
                        .append(" time(s) in the post-fix logs.");
            }
            md.append("\n\n_Analyst: ").append(finding.diagnosis().analyst()).append("_\n");
        }
        Files.writeString(outDir.resolve("report.md"), md.toString());
    }
}
