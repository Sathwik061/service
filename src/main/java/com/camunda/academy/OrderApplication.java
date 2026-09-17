package com.camunda.academy;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.Scanner;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.camunda.academy.handler.OrderHandler;
import com.camunda.academy.handler.PackItemsHandler;
import com.camunda.academy.handler.ProcessPaymentHandler;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;

public class OrderApplication {

    private static final Logger logger = LoggerFactory.getLogger(OrderApplication.class);

    private static final String PROCESS_ID = "orderProcess";
    private static final int NUM_INSTANCES = 1;

    public static void main(String[] args) {

        String zeebeAddress = System.getenv("ZEEBE_ADDRESS");
        String zeebeGrpcAddress = System.getenv("ZEEBE_GRPC_ADDRESS");
        String zeebeRestAddress = System.getenv("ZEEBE_REST_ADDRESS");
        String clientId = System.getenv("ZEEBE_CLIENT_ID");
        String clientSecret = System.getenv("ZEEBE_CLIENT_SECRET");
        String authorizationServerUrl = System.getenv("ZEEBE_AUTHORIZATION_SERVER_URL");

        CamundaClientBuilder builder = CamundaClient.newClientBuilder();

        if (clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank()) {
            OAuthCredentialsProviderBuilder oauthBuilder = new OAuthCredentialsProviderBuilder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .audience("zeebe.camunda.io");
            if (authorizationServerUrl != null && !authorizationServerUrl.isBlank()) {
                oauthBuilder.authorizationServerUrl(authorizationServerUrl);
            }
            // Prefer ZEEBE_GRPC_ADDRESS (includes grpcs:// scheme); fall back to
            // prefixing ZEEBE_ADDRESS with grpcs://, then hard-coded cluster default.
            String grpcUri = zeebeGrpcAddress != null && !zeebeGrpcAddress.isBlank()
                ? zeebeGrpcAddress
                : (zeebeAddress != null && !zeebeAddress.isBlank()
                    ? "grpcs://" + zeebeAddress
                    : "grpcs://456d1d4f-ccc8-40ca-b157-0a96eeada22c.jfk-1.zeebe.camunda.io:443");
            builder.grpcAddress(URI.create(grpcUri))
                .credentialsProvider(oauthBuilder.build());
            // Set REST address if provided; otherwise force gRPC so deploy
            // commands don't fall back to the default http://0.0.0.0:8080
            if (zeebeRestAddress != null && !zeebeRestAddress.isBlank()) {
                builder.restAddress(URI.create(zeebeRestAddress));
            } else {
                builder.preferRestOverGrpc(false);
            }
        } else {
            // Default to local Camunda 8 instance (c8 run / Docker)
            String localGrpc = zeebeAddress != null ? zeebeAddress : "http://localhost:26500";
            builder.grpcAddress(URI.create(localGrpc))
                .restAddress(URI.create("http://localhost:8080"))
                .preferRestOverGrpc(false);
        }

        // ── Health-check HTTP server (satisfies Render Web Service port scan) ──
        // Uses JDK built-in HttpServer — no extra dependencies.
        // Render expects a port to be open; workers use outbound gRPC only.
        try {
            int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "10000"));
            HttpServer healthServer = HttpServer.create(new InetSocketAddress(port), 0);
            healthServer.createContext("/", exchange -> {
                byte[] body = "OK".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.getResponseBody().close();
            });
            healthServer.setExecutor(null);
            healthServer.start();
            logger.info("Health-check server listening on port {}", port);
        } catch (Exception e) {
            logger.warn("Could not start health-check server: {}", e.getMessage());
        }

        try (final CamundaClient client = builder.build()) {

            // Deploy order.bpmn to ensure the process definition is up-to-date
            logger.info("Deploying order.bpmn...");
            client.newDeployResourceCommand()
                .addResourceFromClasspath("order.bpmn")
                .send()
                .join();
            logger.info("order.bpmn deployed successfully.");

            // Process Instance creator looper
            startProcessInstances(client, NUM_INSTANCES);

            final JobWorker orderWorker = client.newWorker()
                .jobType("trackOrderStatus")
                .handler(new OrderHandler())
                .open();
            
            final JobWorker processPaymentWorker = client.newWorker()
                .jobType("processPayment")
                .handler(new ProcessPaymentHandler())
                .open();
        
            final JobWorker packItemsWorker = client.newWorker()
                .jobType("packItems")
                .handler(new PackItemsHandler())
                .open();
             
            // Keep workers running continuously until process is terminated
            logger.info("Java Zeebe Workers active (trackOrderStatus, processPayment, packItems)...");
            Thread.currentThread().join();

            orderWorker.close();
            processPaymentWorker.close();
            packItemsWorker.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void startProcessInstances(CamundaClient camundaClient, int numInstances) {
        logger.info("Starting: " + numInstances + " process instances for process: " + PROCESS_ID);
        for (int i = 0; i < numInstances; i++) {
            FakeRandomizer fakeRandomizer = new FakeRandomizer();
            Map<String, Object> fakeRequest = fakeRandomizer.getRandom();
            logger.info("Generating Order({})",fakeRequest.get("orderId"));
            var event = camundaClient.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(fakeRequest)
                .send()
                .join();
            logger.info("Process instance: {} started", event.getProcessInstanceKey());
        }
        logger.info("Ending: " + numInstances + " instances created for process: " + PROCESS_ID);
    }
}
