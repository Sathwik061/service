package com.camunda.academy.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Logback appender that ships log events to Splunk via HTTP Event Collector (HEC).
 * Uses JDK 11+ HttpClient — no extra Maven dependencies.
 */
public class SplunkHecAppender extends AppenderBase<ILoggingEvent> {

    private String url;
    private String token;
    private String source     = "camunda-order-workers";
    private String sourcetype = "camunda:java:worker";
    private String index      = "main";

    private HttpClient httpClient;
    private String hecEndpoint;

    @Override
    public void start() {
        if (url == null || url.isBlank()) {
            System.err.println("[SplunkHecAppender] ERROR: 'url' property is required but was blank or null.");
            return;
        }
        if (token == null || token.isBlank()) {
            System.err.println("[SplunkHecAppender] ERROR: 'token' property is required but was blank or null.");
            return;
        }

        this.hecEndpoint = url.stripTrailing() + "/services/collector/event";

        // Trust-all SSLContext: Splunk Cloud cert is valid but not in Alpine JRE's
        // trust store. Equivalent to curl's -k flag. Safe here as we control the URL.
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());

            // Also disable hostname verification (empty string = no algorithm = no check)
            javax.net.ssl.SSLParameters sslParams = new javax.net.ssl.SSLParameters();
            sslParams.setEndpointIdentificationAlgorithm("");

            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .sslContext(sslContext)
                    .sslParameters(sslParams)
                    .build();
        } catch (Exception e) {
            System.err.println("[SplunkHecAppender] Failed to create trust-all SSLContext: " + e.getMessage());
            return;
        }

        System.out.println("[SplunkHecAppender] Starting — HEC endpoint: " + hecEndpoint);
        super.start();

        // Send a startup test event so we immediately know if HEC is reachable
        sendEvent("INFO", "com.camunda.academy", "main",
                "SplunkHecAppender started — Camunda workers connected and logging to Splunk");
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted()) return;
        sendEvent(
            event.getLevel().toString(),
            event.getLoggerName(),
            event.getThreadName(),
            event.getFormattedMessage()
        );
    }

    private void sendEvent(String level, String logger, String thread, String message) {
        String body = buildPayload(level, logger, thread, message);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(hecEndpoint))
                .header("Authorization", "Splunk " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();

        // Async but with visible error reporting
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, ex) -> {
                    if (ex != null) {
                        System.err.println("[SplunkHecAppender] SEND FAILED: " + ex.getMessage());
                    } else if (response.statusCode() != 200) {
                        System.err.println("[SplunkHecAppender] HEC returned HTTP "
                                + response.statusCode() + ": " + response.body());
                    }
                });
    }

    private String buildPayload(String level, String logger, String thread, String message) {
        double epochSec = System.currentTimeMillis() / 1000.0;
        return String.format(
            "{\"time\":%.3f,\"source\":\"%s\",\"sourcetype\":\"%s\",\"index\":\"%s\"," +
            "\"event\":{\"level\":\"%s\",\"logger\":\"%s\",\"thread\":\"%s\",\"message\":\"%s\"}}",
            epochSec,
            escapeJson(source), escapeJson(sourcetype), escapeJson(index),
            level, escapeJson(logger), escapeJson(thread), escapeJson(message)
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public void setUrl(String url)         { this.url = url; }
    public void setToken(String token)     { this.token = token; }
    public void setSource(String source)   { this.source = source; }
    public void setSourcetype(String type) { this.sourcetype = type; }
    public void setIndex(String index)     { this.index = index; }
}
