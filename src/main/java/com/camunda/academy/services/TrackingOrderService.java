package com.camunda.academy.services;

import java.time.Duration;
import io.camunda.client.api.response.ActivatedJob;

public class TrackingOrderService {

    // Service duration in seconds (set to 0 for instant execution)
    private static final int TRACKING_TIME = 0;

    public void trackOrderStatus(ActivatedJob job) throws InterruptedException {
        // Instant completion
    }

    public Boolean packItems(ActivatedJob job) throws InterruptedException {
        return true;
    }

    public String processPayment(ActivatedJob job) throws InterruptedException {
        return String.valueOf(System.currentTimeMillis());
    }
}
