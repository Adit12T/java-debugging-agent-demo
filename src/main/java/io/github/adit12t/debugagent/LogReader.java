package io.github.adit12t.debugagent;

import io.github.adit12t.debugagent.Model.LogEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads a Kibana-style export: one JSON document per line (NDJSON). */
public class LogReader {

    /**
     * Lines that are not JSON objects, or have no usable {@code @timestamp}, are skipped with a
     * warning instead of stopping the run: real exports contain truncated and non-JSON lines.
     */
    public List<LogEntry> read(Path file) throws IOException {
        List<LogEntry> entries = new ArrayList<>();
        int lineNumber = 0;
        int skipped = 0;
        for (String line : Files.readAllLines(file)) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            try {
                Map<String, Object> doc = Json.parseObject(line);
                entries.add(new LogEntry(
                        Instant.parse(text(doc, "@timestamp")),
                        text(doc, "level"),
                        text(doc, "service"),
                        text(doc, "message"),
                        doc.get("stack_trace") instanceof String trace ? trace : null));
            } catch (RuntimeException e) {
                if (skipped++ == 0) {
                    System.err.println("Skipping unreadable log line " + lineNumber + " in " + file.getFileName()
                            + " (" + e.getMessage() + ")");
                }
            }
        }
        if (skipped > 1) {
            System.err.println("Skipped " + skipped + " unreadable log lines in " + file.getFileName());
        }
        return entries;
    }

    private static String text(Map<String, Object> doc, String field) {
        return doc.get(field) instanceof String value ? value : "";
    }
}
