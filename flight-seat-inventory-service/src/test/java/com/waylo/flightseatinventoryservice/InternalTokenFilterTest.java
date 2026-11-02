package com.waylo.flightseatinventoryservice;

import com.waylo.flightseatinventoryservice.filter.InternalTokenFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InternalTokenFilterTest {

    private MockHttpServletResponse run(String token, String path, String header, boolean pass) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        if (header != null) req.addHeader("X-Internal-Token", header);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        new InternalTokenFilter(token).doFilter(req, res, chain);
        verify(chain, pass ? times(1) : never()).doFilter(any(), any());
        return res;
    }

    @Test
    void internalNeedsToken() throws Exception {
        run("tok", "/internal/flight-holds", null, false);
        run("tok", "/internal/flight-holds", "bad", false);
        run("tok", "/internal/flight-holds", "tok", true);
    }

    @Test
    void failsClosedWithoutConfiguredToken() throws Exception {
        assertEquals(401, run("", "/internal/flight-holds", "", false).getStatus());
    }

    @Test
    void otherPathsUntouched() throws Exception {
        run("tok", "/api/flight-offers", null, true);
    }
}
