package com.camunda.academy.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobHandler;

public class ProcessPaymentHandler implements JobHandler {

    private static final Logger logger = LoggerFactory.getLogger(ProcessPaymentHandler.class);

    @Override
    public void handle(JobClient client, ActivatedJob job) throws Exception {
        logger.info("Handling job: {} Processing payment", job.getKey());

        // Simulate payment processing
        String orderId = (String) job.getVariablesAsMap().getOrDefault("orderId", "unknown");
        logger.info("Handling job: {} Payment processed successfully for order {}", job.getKey(), orderId);

        client.newCompleteCommand(job.getKey())
            .variables(java.util.Map.of("paymentStatus", "COMPLETED"))
            .send()
            .join();
    }
}
