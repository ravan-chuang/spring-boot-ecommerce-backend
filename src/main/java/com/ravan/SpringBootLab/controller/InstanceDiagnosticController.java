package com.ravan.SpringBootLab.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Profile({"local", "ha"})
public class InstanceDiagnosticController {

    @GetMapping("/internal/instance")
    public ResponseEntity<Map<String, Object>> instance() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(
                "hostname",
                System.getenv().getOrDefault("HOSTNAME", "unknown")
        );
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(body);
    }
}
