package com.camunda.academy.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import javax.net.ssl.*;
import java.io.OutputStream;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Logback appender — ships logs to Splunk HEC via HTTPS.
 * Uses HttpsURLConnection (not HttpClient) so HostnameVerifier can be overridden,
 * bypassing Alpine JRE's incomplete SSL trust store (equivalent to curl -k).
 */
public class SplunkHecAppender extends AppenderBase<ILoggingEvent> {

    private String url;
    private String token;
    private String source     = "camunda-order-workers";
    private String sourcetype = "camunda:java:worker";
    private String index      = "main";

    private SSLSocketFactory sslSocketFactory;
    private HostnameVerifier trustAllHostnames;
    private String hecEndpoint;
    private ExecutorService executor;

    @Override
    public void start() {
        if (url == null || url.isBlank()) {
            System.err.println("[SplunkHecAppender] ERROR: 'url' is required.");
            return;
        }
        if (token == null || token.isBlank()) {
            System.err.println("[SplunkHecAppender] ERROR: 'token' is required.");
            return;
        }

        this.hecEndpoint = url.stripTrailing() + "/services/collector/event";

        // Trust-all SSL + hostname verifier — same as curl -k
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
            };
            SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(null, trustAll, new SecureRandom());
            this.sslSocketFactory = sslCtx.getSocketFactory();
            this.trustAllHostnames = (hostname, session) -> true;  // trust any hostname
        } catch (Exception e) {
            System.err.println("[SplunkHecAppender] SSL setup failed: " + e.getMessage());
            return;
        }

        // Single-threaded background executor for async sends
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "splunk-hec-sender");
            t.setDaemon(true);
            return t;
        });

        System.out.println("[SplunkHecAppender] Started — endpoint: " + hecEndpoint);
        super.start();

        // Fire a startup test event immediately
        sendAsync("INFO", "com.camunda.academy", "main",
            "SplunkHecAppender started — Camunda workers logging to Splunk");
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted()) return;
        sendAsync(
            event.getLevel().toString(),
            event.getLoggerName(),
            event.getThreadName(),
            event.getFormattedMessage()
        );
    }

    private void sendAsync(String level, String logger, String thread, String message) {
        String payload = buildPayload(level, logger, thread, message);
        executor.submit(() -> {
            try {
                URL u = new URL(hecEndpoint);
                HttpsURLConnection conn = (HttpsURLConnection) u.openConnection();
                conn.setSSLSocketFactory(sslSocketFactory);
                conn.setHostnameVerifier(trustAllHostnames);  // ← fully disables hostname check
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Splunk " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes());
                }

                int code = conn.getResponseCode();
                if (code != 200) {
                    System.err.println("[SplunkHecAppender] HEC returned HTTP " + code);
                }
                conn.disconnect();
            } catch (Exception e) {
                System.err.println("[SplunkHecAppender] SEND FAILED: " + e.getMessage());
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
