package com.waylo.carrentalservice.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CarRentalAccessFilterTest {

    private static final String TOKEN = "t".repeat(40);
    private final CarRentalAccessFilter filter = new CarRentalAccessFilter(new ObjectMapper(), TOKEN);
    private final String uid = UUID.randomUUID().toString();

    private int run(String method, String path, String user, String role, String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        if (user != null) req.addHeader("X-User-Id", user);
        if (role != null) req.addHeader("X-User-Role", role);
        if (token != null) req.addHeader("X-Internal-Token", token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        return chain.getRequest() == null ? res.getStatus() : 200;
    }

    @Test
    void internalPathNeedsToken() throws Exception {
        assertThat(run("POST", "/internal/car-holds", null, null, null)).isEqualTo(401);
        assertThat(run("POST", "/internal/car-holds", null, null, "wrong")).isEqualTo(401);
        assertThat(run("POST", "/internal/car-holds", null, null, TOKEN)).isEqualTo(200);
    }

    @Test
    void partnerPathNeedsUserId() throws Exception {
        assertThat(run("GET", "/api/partner/organizations/x/branches", null, null, null)).isEqualTo(401);
        assertThat(run("GET", "/api/partner/organizations/x/branches", "bad", null, null)).isEqualTo(401);
        assertThat(run("GET", "/api/partner/organizations/x/branches", uid, null, null)).isEqualTo(200);
    }

    @Test
    void adminPathNeedsAdminRole() throws Exception {
        assertThat(run("GET", "/api/admin/car-documents", uid, "CUSTOMER", null)).isEqualTo(403);
        assertThat(run("GET", "/api/admin/car-documents", uid, null, null)).isEqualTo(403);
        assertThat(run("GET", "/api/admin/car-documents", uid, "ADMIN", null)).isEqualTo(200);
    }

    @Test
    void publicCataloguePathsAreOpen() throws Exception {
        assertThat(run("GET", "/api/car-offers", null, null, null)).isEqualTo(200);
        assertThat(run("GET", "/api/car-branches", null, null, null)).isEqualTo(200);
    }

    @Test
    void rentalsNeedUserId() throws Exception {
        assertThat(run("GET", "/api/rentals/me", null, null, null)).isEqualTo(401);
        assertThat(run("GET", "/api/rentals/me", uid, null, null)).isEqualTo(200);
    }
}
