package com.waylo.flightseatinventoryservice;

import com.waylo.flightseatinventoryservice.filter.SeatAccessFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeatAccessFilterTest {

    private final SeatAccessFilter filter = new SeatAccessFilter();
    private final String uid = UUID.randomUUID().toString();

    private MockHttpServletResponse run(String method, String path, String userId, String role,
                                        boolean expectPass) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        if (userId != null) req.addHeader("X-User-Id", userId);
        if (role != null) req.addHeader("X-User-Role", role);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        verify(chain, expectPass ? times(1) : never()).doFilter(any(), any());
        return res;
    }

    private static final String B = "/api/seat-inventory";

    @Test
    void readsAreOpen() throws Exception {
        run("GET", B + "/seats/TK100", null, null, true);
        run("HEAD", B + "/seats/TK100", null, null, true);
        run("OPTIONS", B + "/seats", null, null, true);
    }

    @Test
    void reserveWithoutUserIdIs401() throws Exception {
        assertEquals(401, run("POST", B + "/seats/TK100/reserve", null, null, false).getStatus());
        assertEquals(401, run("DELETE", B + "/seats/TK100/reserve", "  ", null, false).getStatus());
    }

    @Test
    void reserveWithNonUuidIs401() throws Exception {
        MockHttpServletResponse r = run("POST", B + "/seats/TK100/reserve", "abc", null, false);
        assertEquals(401, r.getStatus());
        assertTrue(r.getContentAsString().contains("\"error\":\"UNAUTHORIZED\""));
    }

    @Test
    void reserveWithUuidPassesForPlainUser() throws Exception {
        run("POST", B + "/seats/TK100/reserve", uid, "USER", true);
        run("DELETE", B + "/seats/TK100/reserve", uid, null, true);
    }

    @Test
    void adminCannotReserveButCanRelease() throws Exception {
        MockHttpServletResponse r = run("POST", B + "/seats/TK100/reserve", uid, "ADMIN", false);
        assertEquals(403, r.getStatus());
        assertTrue(r.getContentAsString().contains("ADMIN_CANNOT_PURCHASE"));
        run("DELETE", B + "/seats/TK100/reserve", uid, "ADMIN", true);
    }

    @Test
    void adminWritesWithoutUserIdIs401() throws Exception {
        assertEquals(401, run("POST", B + "/seats", null, "ADMIN", false).getStatus());
    }

    @Test
    void adminWritesByNonAdminIs403() throws Exception {
        assertEquals(403, run("POST", B + "/seats", uid, "USER", false).getStatus());
        assertEquals(403, run("POST", B + "/seats/bulk", uid, null, false).getStatus());
        assertEquals(403, run("DELETE", B + "/seats/TK100/12A", uid, "USER", false).getStatus());
    }

    @Test
    void adminWritesByAdminPass() throws Exception {
        run("POST", B + "/seats", uid, "ADMIN", true);
        run("POST", B + "/seats/bulk", uid, "ADMIN", true);
        run("DELETE", B + "/seats/TK100/12A", uid, "ADMIN", true);
    }

    @Test
    void otherPathsUntouched() throws Exception {
        run("POST", "/actuator/x", null, null, true);
    }
}
