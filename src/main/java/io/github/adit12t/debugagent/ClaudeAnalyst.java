package io.github.adit12t.debugagent;

import io.github.adit12t.debugagent.Model.Diagnosis;
import io.github.adit12t.debugagent.Model.ErrorGroup;
import io.github.adit12t.debugagent.Model.SourceSnippet;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Optional analyst that asks Claude for the diagnosis and a complete regression test.
 * Used only when ANTHROPIC_API_KEY is set. Any failure falls back to the offline analyst,
 * so a network or API problem never stops the run.
 */
public class ClaudeAnalyst implements Analyst {

    private static final URI ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");

    private final String apiKey;
    private final String model;
    private final Analyst fallback;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public ClaudeAnalyst(String apiKey, String model, Analyst fallback) {
        this.apiKey = apiKey;
        this.model = model;
        this.fallback = fallback;
    }

    @Override
    public Diagnosis diagnose(ErrorGroup group, SourceSnippet snippet) {
        if (snippet == null) {
            return fallback.diagnose(group, null);
        }
        try {
            String body = "{\"model\":" + Json.quote(model)
                    + ",\"max_tokens\":1500,\"messages\":[{\"role\":\"user\",\"content\":"
                    + Json.quote(prompt(group, snippet)) + "}]}";

            HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(90))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("Claude request failed (HTTP " + response.statusCode() + "); using offline analyst.");
                return fallback.diagnose(group, snippet);
            }

            StringBuilder text = new StringBuilder();
            if (Json.parseObject(response.body()).get("content") instanceof List<?> blocks) {
                for (Object block : blocks) {
                    if (block instanceof Map<?, ?> map && "text".equals(map.get("type"))
                            && map.get("text") instanceof String part) {
                        text.append(part);
                    }
                }
            }
            Map<String, Object> answer = Json.parseObject(extractJson(text.toString()));
            return new Diagnosis(
                    String.valueOf(answer.get("rootCause")),
                    String.valueOf(answer.get("suggestedFix")),
                    String.valueOf(answer.get("regressionTest")),
                    "Claude (" + model + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback.diagnose(group, snippet);
        } catch (Exception e) {
            System.err.println("Claude analysis failed (" + e.getMessage() + "); using offline analyst.");
            return fallback.diagnose(group, snippet);
        }
    }

    private String prompt(ErrorGroup group, SourceSnippet snippet) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < snippet.lines().size(); i++) {
            code.append(snippet.startLine() + i).append(": ").append(snippet.lines().get(i)).append('\n');
        }
        return """
                You are helping debug a Java service. An exception was logged %d times.

                Exception: %s: %s
                Location: %s.%s, line %d

                Source around the failing line:
                %s
                Reply with one JSON object and nothing else, with these string fields:
                "rootCause": why the exception happens, in two sentences at most.
                "suggestedFix": the smallest safe code change, described in plain words.
                "regressionTest": a complete JUnit 5 test class that fails before the fix and passes after it.
                """.formatted(group.count(), group.trace().exceptionType(), group.trace().exceptionMessage(),
                group.appFrame().className(), group.appFrame().method(), group.appFrame().line(), code);
    }

    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("no JSON object in the reply");
        }
        return text.substring(start, end + 1);
    }
}
