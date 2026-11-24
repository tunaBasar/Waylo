package com.waylo.packagebookingservice.tenant;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
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
        assertEquals(TenantContext.Mode.SERVICE, modeFor("/internal/orders/items/1", null));
    }

    @Test
    void internalPrefixWithoutSegmentBoundaryIsPublic() throws Exception {
        assertEquals(TenantContext.Mode.PUBLIC, modeFor("/internalfoo", null));
    }

    @Test
    void apiPathWithAdminRoleIsAdmin() throws Exception {
        assertEquals(TenantContext.Mode.ADMIN, modeFor("/api/admin/orders", "ADMIN"));
    }

    @Test
    void customerAndPartnerPathsStartPublicUntilAccessCheckPasses() throws Exception {
        assertEquals(TenantContext.Mode.PUBLIC, modeFor("/api/orders", null));
        assertEquals(TenantContext.Mode.PUBLIC, modeFor("/api/partner/organizations/" + UUID.randomUUID() + "/order-items", "USER"));
    }

    @Test
    void authorizePartnerUpgradesPublicOnlyAndNeverAdminOrService() throws Exception {
        UUID org = UUID.randomUUID();
        TenantContext.set(TenantContext.Mode.PUBLIC);
        TenantContext.authorizePartner(org);
        assertEquals(TenantContext.Mode.PARTNER, TenantContext.current().mode());
        assertEquals(org.toString(), TenantContext.current().orgIdsCsv());

        TenantContext.set(TenantContext.Mode.ADMIN);
        TenantContext.authorizePartner(org);
        assertEquals(TenantContext.Mode.ADMIN, TenantContext.current().mode());

        TenantContext.set(TenantContext.Mode.SERVICE);
        TenantContext.authorizePartner(org);
        assertEquals(TenantContext.Mode.SERVICE, TenantContext.current().mode());
    }

    @Test
    void contextIsClearedAfterFilterReturns() throws Exception {
        assertEquals(TenantContext.Mode.PUBLIC, modeFor("/api/orders", null));
        // With no thread-local state current() falls back to the SERVICE default (jobs), so PUBLIC must be gone.
        assertEquals(TenantContext.Mode.SERVICE, TenantContext.current().mode());
    }

    @Test
    void contextIsClearedWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.setRequestURI("/api/orders");
        assertThrows(ServletException.class, () -> filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> {
                    assertEquals(TenantContext.Mode.PUBLIC, TenantContext.current().mode());
                    throw new ServletException("boom");
                }));
        assertEquals(TenantContext.Mode.SERVICE, TenantContext.current().mode());
    }
}
