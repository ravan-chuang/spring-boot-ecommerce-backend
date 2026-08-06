package com.ravan.SpringBootLab.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdsTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void normalizesSafeValuesAndGeneratesForUnsafeValues() {
        assertThat(CorrelationIds.normalizeOrGenerate("  request-123  "))
                .isEqualTo("request-123");
        assertThatCodeIsUuid(CorrelationIds.normalizeOrGenerate("bad value!"));
        assertThatCodeIsUuid(CorrelationIds.normalizeOrGenerate(null));
    }

    @Test
    void resolvesCurrentCorrelationThenTraceThenGeneratedValue() {
        MDC.put("traceId", "trace-123");
        assertThat(CorrelationIds.currentOrGenerate()).isEqualTo("trace-123");

        MDC.put(CorrelationIds.MDC_KEY, "correlation-123");
        assertThat(CorrelationIds.currentOrGenerate())
                .isEqualTo("correlation-123");

        MDC.clear();
        assertThatCodeIsUuid(CorrelationIds.currentOrGenerate());
    }

    @Test
    void filterExposesCorrelationAndRestoresPreviousMdcValue() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIds.HTTP_HEADER, "request-456");
        MDC.put(CorrelationIds.MDC_KEY, "outer-request");

        filter.doFilterInternal(request, response, (ignoredRequest, ignoredResponse) ->
                assertThat(MDC.get(CorrelationIds.MDC_KEY))
                        .isEqualTo("request-456")
        );

        assertThat(response.getHeader(CorrelationIds.HTTP_HEADER))
                .isEqualTo("request-456");
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isEqualTo("outer-request");
    }

    @Test
    void filterRemovesMdcValueWhenNoPreviousContextExists() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (ignoredRequest, ignoredResponse) ->
                assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNotBlank()
        );

        assertThatCodeIsUuid(response.getHeader(CorrelationIds.HTTP_HEADER));
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull();
    }

    private void assertThatCodeIsUuid(String value) {
        assertThat(UUID.fromString(value)).isNotNull();
    }
}
