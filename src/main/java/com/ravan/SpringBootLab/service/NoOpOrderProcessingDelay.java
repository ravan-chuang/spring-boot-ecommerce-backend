package com.ravan.SpringBootLab.service;

import org.springframework.stereotype.Component;

@Component
public class NoOpOrderProcessingDelay implements OrderProcessingDelay {

    @Override
    public void delay() {
        // Intentionally no-op in production.
    }
}
