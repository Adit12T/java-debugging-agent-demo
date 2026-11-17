package io.github.adit12t.debugagent;

import io.github.adit12t.debugagent.Model.LogEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogReaderTest {

    @Test
    void skipsLinesThatAreNotUsableLogDocuments() throws IOException {
        Path file = Files.createTempFile("logs", ".ndjson");
        try {
            Files.writeString(file, String.join("\n",
                    "{\"@timestamp\":\"2026-01-05T10:00:00Z\",\"level\":\"ERROR\",\"message\":\"first\"}",
                    "",
                    "this line is not JSON",
                    "{\"level\":\"ERROR\",\"message\":\"no timestamp\"}",
                    "{\"@timestamp\":\"yesterday\",\"level\":\"ERROR\"}",
                    "[1, 2, 3]",
                    "{\"@timestamp\":\"2026-01-05T10:00:05Z\",\"level\":\"INFO\",\"message\":\"last\"}"));

            List<LogEntry> entries = new LogReader().read(file);

            assertEquals(List.of("first", "last"), entries.stream().map(LogEntry::message).toList());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void rejectsUnknownOptionsAndMissingValues() {
        assertEquals("Unknown option: --log", assertThrows(IllegalArgumentException.class,
                () -> DebugAgent.parse(new String[] {"--log", "x.ndjson"})).getMessage());
        assertEquals("Option --logs needs a value", assertThrows(IllegalArgumentException.class,
                () -> DebugAgent.parse(new String[] {"--logs"})).getMessage());
        assertEquals("x.ndjson", DebugAgent.parse(new String[] {"--logs", "x.ndjson"}).get("logs"));
    }
}
