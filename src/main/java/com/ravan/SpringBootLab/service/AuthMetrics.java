package com.ravan.SpringBootLab.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class AuthMetrics {

    private final MeterRegistry meterRegistry;

    public AuthMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordSuccess(String action) {
        record(action, "success");
    }

    public void recordFailure(String action) {
        record(action, "failure");
    }

    private void record(String action, String outcome) {
        meterRegistry.counter(
                "auth.events",
                "action", action,
                "outcome", outcome
        ).increment();
    }
}
