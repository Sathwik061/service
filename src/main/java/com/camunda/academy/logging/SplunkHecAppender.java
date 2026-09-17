package com.camunda.academy.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Logback appender that ships log events to Splunk via HTTP Event Collector (HEC).
 * <p>
 * Configuration (logback.xml):
 * <pre>
 *   &lt;appender name="SPLUNK" class="com.camunda.academy.logging.SplunkHecAppender"&gt;
 *     &lt;url&gt;https://inputs.prd-p-0hb92.splunkcloud.com:8088&lt;/url&gt;
 *     &lt;token&gt;YOUR-HEC-TOKEN&lt;/token&gt;
 *     &lt;source&gt;camunda-order-workers&lt;/source&gt;
 *     &lt;sourcetype&gt;camunda:java:worker&lt;/sourcetype&gt;
 *     &lt;index&gt;main&lt;/index&gt;
 *   &lt;/appender&gt;
 * </pre>
 *
 * No extra Maven dependencies — uses JDK 11+ HttpClient.
 */
public class SplunkHecAppender extends AppenderBase<ILoggingEvent> {

    // ── Logback-injected configuration properties ─────────────────────────

    private String url;
    private String token;
    private String source      = "camunda-order-workers";
    private String sourcetype  = "camunda:java:worker";
    private String index       = "main";

    // ── Internal state ────────────────────────────────────────────────────

    private HttpClient httpClient;
    private String hecEndpoint;

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public void start() {
        if (url == null || url.isBlank()) {
            addError("SplunkHecAppender: 'url' property is required.");
            return;
        }
        if (token == null || token.isBlank()) {
            addError("SplunkHecAppender: 'token' property is required.");
            return;
        }

        this.hecEndpoint = url.stripTrailing() + "/services/collector/event";

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        super.start();
    }

    // ── Core: called for every log event ─────────────────────────────────

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted()) return;

        String body = buildPayload(event);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(hecEndpoint))
                .header("Authorization", "Splunk " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build();

        // Fire-and-forget: send async, ignore failures to not block worker threads
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(ex -> {
                    addWarn("SplunkHecAppender: failed to send log to Splunk: " + ex.getMessage());
                    return null;
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String buildPayload(ILoggingEvent event) {
        double epochSec = event.getTimeStamp() / 1000.0;
        String msg = escapeJson(event.getFormattedMessage());
        String level = event.getLevel().toString();
        String logger = escapeJson(event.getLoggerName());
        String thread = escapeJson(event.getThreadName());

        // Build Splunk HEC JSON payload
        return String.format(
            "{\"time\":%.3f,\"source\":\"%s\",\"sourcetype\":\"%s\",\"index\":\"%s\"," +
            "\"event\":{\"level\":\"%s\",\"logger\":\"%s\",\"thread\":\"%s\",\"message\":\"%s\"}}",
            epochSec, source, sourcetype, index,
            level, logger, thread, msg
        );
    }

    /** Minimal JSON string escaping. */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ── Setters (called by Logback XML binding) ───────────────────────────

    public void setUrl(String url)             { this.url = url; }
    public void setToken(String token)         { this.token = token; }
    public void setSource(String source)       { this.source = source; }
    public void setSourcetype(String type)     { this.sourcetype = type; }
    public void setIndex(String index)         { this.index = index; }
}
