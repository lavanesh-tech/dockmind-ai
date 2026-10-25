package com.dockmind.common.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private static final String GENERATED_ID = "generated-id";

    private final CorrelationIdFilter filter = new CorrelationIdFilter(() -> GENERATED_ID);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/x"), response, (req, res) -> { });

        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(GENERATED_ID);
    }

    @Test
    void reusesValidIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader(CorrelationId.HEADER, "gateway-7f3a.42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("gateway-7f3a.42");
        assertThat(request.getAttribute(CorrelationId.REQUEST_ATTRIBUTE)).isEqualTo("gateway-7f3a.42");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",                       // empty
            "abc\r\nFAKE LOG LINE",   // log injection attempt
            "has space",
            "<script>",
            "0123456789012345678901234567890123456789012345678901234567890123456789" // 70 chars
    })
    void replacesInvalidIncomingId(String badId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader(CorrelationId.HEADER, badId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(GENERATED_ID);
    }

    @Test
    void idIsInMdcDuringRequestAndRemovedAfterwards() throws Exception {
        AtomicReference<String> seenInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenInsideChain.set(MDC.get(CorrelationId.MDC_KEY));

        filter.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), chain);

        assertThat(seenInsideChain.get()).isEqualTo(GENERATED_ID);
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void mdcIsClearedEvenWhenDownstreamThrows() {
        FilterChain failingChain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        // The filter must not swallow the exception...
        assertThatThrownBy(() -> filter.doFilter(
                new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), failingChain))
                .hasMessage("boom");

        // ...but it must still clean up the thread-local MDC.
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void defaultGeneratorProducesValidIds() {
        String id = new CorrelationIdFilter().resolve(null);

        assertThat(CorrelationId.isValid(id)).isTrue();
    }
}
