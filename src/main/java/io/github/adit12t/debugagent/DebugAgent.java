package io.github.adit12t.debugagent;

import io.github.adit12t.debugagent.Model.ErrorGroup;
import io.github.adit12t.debugagent.Model.Finding;
import io.github.adit12t.debugagent.Model.LogEntry;
import io.github.adit12t.debugagent.Model.SourceSnippet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the pipeline: read logs, group errors, locate the source line, diagnose,
 * draft a regression test, then verify against post-fix logs.
 */
public class DebugAgent {

    private static final Set<String> OPTIONS = Set.of("logs", "after-logs", "source-root", "app-package", "out");
    private static final String USAGE = "Usage: DebugAgent [--logs FILE] [--after-logs FILE] "
            + "[--source-root DIR] [--app-package PACKAGE] [--out DIR]";

    public static void main(String[] args) {
        try {
            runCli(parse(args));
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(USAGE);
            System.exit(2);
        } catch (NoSuchFileException e) {
            System.err.println("File or folder not found: " + e.getFile());
            System.exit(2);
        } catch (IOException e) {
            System.err.println("Could not read or write files: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runCli(Map<String, String> options) throws IOException {
        Path logs = Path.of(options.getOrDefault("logs", "sample-data/logs-before.ndjson"));
        Path afterLogs = Path.of(options.getOrDefault("after-logs", "sample-data/logs-after.ndjson"));
        Path sourceRoot = Path.of(options.getOrDefault("source-root", "sample-app/src/main/java"));
        String appPackage = options.getOrDefault("app-package", "demo.orders");
        Path outDir = Path.of(options.getOrDefault("out", "out"));

        List<Finding> findings = run(logs, Files.exists(afterLogs) ? afterLogs : null, sourceRoot, appPackage,
                chooseAnalyst());
        new ReportWriter().write(findings, outDir);

        for (Finding finding : findings) {
            ErrorGroup group = finding.group();
            String where = group.appFrame() == null ? "unknown location"
                    : group.appFrame().file() + ":" + group.appFrame().line();
            String status = !finding.verification().checked() ? "not verified"
                    : finding.verification().resolved() ? "RESOLVED" : "STILL OCCURRING";
            System.out.printf("%-26s x%d  %-28s %s%n", group.trace().simpleType(), group.count(), where, status);
        }
        System.out.println("Report: " + outDir.resolve("report.md"));
    }

    public static List<Finding> run(Path logs, Path afterLogs, Path sourceRoot, String appPackage, Analyst analyst)
            throws IOException {
        LogReader reader = new LogReader();
        ErrorGrouper grouper = new ErrorGrouper(appPackage);
        SourceLocator locator = new SourceLocator(sourceRoot);
        Verifier verifier = new Verifier(grouper);

        List<LogEntry> postFixLogs = afterLogs == null ? null : reader.read(afterLogs);
        List<Finding> findings = new ArrayList<>();
        for (ErrorGroup group : grouper.group(reader.read(logs))) {
            SourceSnippet snippet = locator.locate(group.appFrame());
            findings.add(new Finding(group, snippet, analyst.diagnose(group, snippet),
                    verifier.verify(group, postFixLogs)));
        }
        return findings;
    }

    private static Analyst chooseAnalyst() {
        Analyst offline = new RuleBasedAnalyst();
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return offline;
        }
        String model = System.getenv().getOrDefault("ANTHROPIC_MODEL", "claude-sonnet-5-5");
        return new ClaudeAnalyst(apiKey, model, offline);
    }

    /** Reads "--name value" pairs; rejects unknown options and options without a value. */
    static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            String name = args[i].startsWith("--") ? args[i].substring(2) : null;
            if (name == null || !OPTIONS.contains(name)) {
                throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Option " + args[i] + " needs a value");
            }
            options.put(name, args[i + 1]);
        }
        return options;
    }
}
