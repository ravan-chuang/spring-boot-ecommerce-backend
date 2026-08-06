package com.ravan.SpringBootLab.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String previousCorrelationId = MDC.get(CorrelationIds.MDC_KEY);
        String correlationId = CorrelationIds.normalizeOrGenerate(
                request.getHeader(CorrelationIds.HTTP_HEADER)
        );

        MDC.put(CorrelationIds.MDC_KEY, correlationId);
        response.setHeader(CorrelationIds.HTTP_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousCorrelationId == null) {
                MDC.remove(CorrelationIds.MDC_KEY);
            } else {
                MDC.put(CorrelationIds.MDC_KEY, previousCorrelationId);
            }
        }
    }
}
