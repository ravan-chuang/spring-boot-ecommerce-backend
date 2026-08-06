package com.ravan.SpringBootLab.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIds {

    public static final String HTTP_HEADER = "X-Correlation-ID";
    public static final String KAFKA_HEADER = "correlation-id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE_VALUE =
            Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private CorrelationIds() {
    }

    public static String normalizeOrGenerate(String candidate) {
        if (candidate != null) {
            String trimmed = candidate.trim();

            if (SAFE_VALUE.matcher(trimmed).matches()) {
                return trimmed;
            }
        }

        return UUID.randomUUID().toString();
    }

    public static String currentOrGenerate() {
        String correlationId = MDC.get(MDC_KEY);

        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }

        String traceId = MDC.get("traceId");

        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }

        return UUID.randomUUID().toString();
    }
}
