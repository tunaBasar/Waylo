package com.waylo.carrentalservice.tenant;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private TenantContext.Mode modeFor(String uri, String role) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        if (role != null) {
            request.addHeader("X-User-Role", role);
        }
        AtomicReference<TenantContext.Mode> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.set(TenantContext.current().mode()));
        return seen.get();
    }

    @Test
    void internalRootIsService() throws Exception {
        assertEquals(TenantContext.Mode.SERVICE, modeFor("/internal", null));
    }

    @Test
    void internalSubPathIsService() throws Exception {
        assertEquals(TenantContext.Mode.SERVICE, modeFor("/internal/car-holds/1", null));
    }

    @Test
    void internalPrefixWithoutSegmentBoundaryIsPublic() throws Exception {
        assertEquals(TenantContext.Mode.PUBLIC, modeFor("/internalfoo", null));
    }

    @Test
    void internalPrefixWithoutSegmentBoundaryWithAdminRoleIsAdminNotService() throws Exception {
        assertEquals(TenantContext.Mode.ADMIN, modeFor("/internalfoo", "ADMIN"));
    }

    @Test
    void apiPathWithAdminRoleIsAdmin() throws Exception {
        assertEquals(TenantContext.Mode.ADMIN, modeFor("/api/car-offers", "ADMIN"));
    }

    @Test
    void apiPathWithoutRoleIsPublic() throws Exception {
        assertEquals(TenantContext.Mode.PUBLIC, modeFor("/api/car-offers", null));
    }

    @Test
    void contextIsClearedAfterFilterReturns() throws Exception {
        assertEquals(TenantContext.Mode.PUBLIC, modeFor("/api/car-offers", null));
        // With no thread-local state current() falls back to the SERVICE default, so PUBLIC must be gone.
        assertEquals(TenantContext.Mode.SERVICE, TenantContext.current().mode());
    }

    @Test
    void contextIsClearedWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/car-offers");
        request.setRequestURI("/api/car-offers");
        assertThrows(ServletException.class, () -> filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> {
                    assertEquals(TenantContext.Mode.PUBLIC, TenantContext.current().mode());
                    throw new ServletException("boom");
                }));
        assertEquals(TenantContext.Mode.SERVICE, TenantContext.current().mode());
    }
}
