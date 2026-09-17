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
        logger.error("Handling job: {} Payment task failed! Triggering Sentinel SRE Agent...", job.getKey());

        // Throws BPMN error -> caught by Error Boundary Event in Camunda -> Sentinel investigates & emails RCA
        String errText = "PaymentGatewayTimeout: High error rate (45.2%) on payment-service. DatabaseConnectionPoolExhausted: Unable to acquire connection from pool after 30000ms timeout. Affected service: payment-service";
        
        client.newThrowErrorCommand(job.getKey())
            .errorCode("PROCESS_ERROR")
            .errorMessage(errText)
            .variables(java.util.Map.of("errorMessage", errText))
            .send()
            .join();
    }
}

