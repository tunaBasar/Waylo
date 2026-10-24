package com.waylo.packagebookingservice.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LegacyBookingAccessFilterTest {

    private static final String TOKEN = "t".repeat(40);
    private final LegacyBookingAccessFilter filter = new LegacyBookingAccessFilter(TOKEN);

    private MockHttpServletResponse run(String method, String path, String token, String userId, String role,
                                        boolean expectPass) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        if (token != null) req.addHeader("X-Internal-Token", token);
        if (userId != null) req.addHeader("X-User-Id", userId);
        if (role != null) req.addHeader("X-User-Role", role);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        verify(chain, expectPass ? times(1) : never()).doFilter(any(), any());
        return res;
    }

    @Test
    void anonymousRejected401() throws Exception {
        assertThat(run("GET", "/api/bookings", null, null, null, false).getStatus()).isEqualTo(401);
        assertThat(run("POST", "/api/bookings", null, null, null, false).getStatus()).isEqualTo(401);
    }

    @Test
    void customerRejected403ForAllMethods() throws Exception {
        assertThat(run("POST", "/api/bookings", null, "u1", "USER", false).getStatus()).isEqualTo(403);
        assertThat(run("GET", "/api/bookings/customer/x", null, "u1", "USER", false).getStatus()).isEqualTo(403);
        assertThat(run("POST", "/api/bookings/1/cancel", null, "u1", null, false).getStatus()).isEqualTo(403);
    }

    @Test
    void wrongInternalTokenRejected() throws Exception {
        assertThat(run("POST", "/api/bookings/1/cancel", "wrong", null, null, false).getStatus()).isEqualTo(401);
    }

    @Test
    void validInternalTokenPasses() throws Exception {
        run("POST", "/api/bookings/1/status", TOKEN, null, null, true);
        run("GET", "/api/bookings/flight/1", TOKEN, null, null, true);
    }

    @Test
    void adminReadOnly() throws Exception {
        run("GET", "/api/bookings", null, "a1", "ADMIN", true);
        assertThat(run("POST", "/api/bookings", null, "a1", "ADMIN", false).getStatus()).isEqualTo(403);
        assertThat(run("DELETE", "/api/bookings/1", null, "a1", "ADMIN", false).getStatus()).isEqualTo(403);
    }

    @Test
    void otherPathsUntouched() throws Exception {
        run("GET", "/api/orders", null, null, null, true);
        run("GET", "/api/bookingsX", null, null, null, true);
    }
}
