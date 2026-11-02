package com.waylo.crmservice.controller;

import com.waylo.crmservice.filter.CrmAccessFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CrmAccessFilterTest {

    private static final String TOKEN = "t".repeat(40);
    private final CrmAccessFilter filter = new CrmAccessFilter(TOKEN);

    private int call(String method, String uri, String userId, String role, String internal) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRequestURI(uri);
        if (userId != null) req.addHeader("X-User-Id", userId);
        if (role != null) req.addHeader("X-User-Role", role);
        if (internal != null) req.addHeader("X-Internal-Token", internal);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        return verifyingPassed(chain) ? 200 : res.getStatus();
    }

    private boolean verifyingPassed(FilterChain chain) throws Exception {
        try { verify(chain).doFilter(any(), any()); return true; } catch (AssertionError e) { return false; }
    }

    @Test void anonymous_401() throws Exception { assertThat(call("GET", "/api/customers/me", null, null, null)).isEqualTo(401); }
    @Test void userMe_allowed() throws Exception { assertThat(call("GET", "/api/customers/me", "u", "USER", null)).isEqualTo(200); }
    @Test void userList_403() throws Exception { assertThat(call("GET", "/api/customers", "u", "USER", null)).isEqualTo(403); }
    @Test void userReadOther_403() throws Exception { assertThat(call("GET", "/api/customers/1234", "u", "USER", null)).isEqualTo(403); }
    @Test void userSearch_403() throws Exception { assertThat(call("GET", "/api/customers/search", "u", "USER", null)).isEqualTo(403); }
    @Test void userByEmail_403() throws Exception { assertThat(call("GET", "/api/customers/email/a@b.c", "u", "USER", null)).isEqualTo(403); }
    @Test void userByTier_403() throws Exception { assertThat(call("GET", "/api/customers/tier/GOLD", "u", "USER", null)).isEqualTo(403); }
    @Test void userPut_403() throws Exception { assertThat(call("PUT", "/api/customers/1", "u", "USER", null)).isEqualTo(403); }
    @Test void userDelete_403() throws Exception { assertThat(call("DELETE", "/api/customers/1", "u", "USER", null)).isEqualTo(403); }
    @Test void userPost_403() throws Exception { assertThat(call("POST", "/api/customers", "u", "USER", null)).isEqualTo(403); }
    @Test void userLoyaltyChange_403() throws Exception { assertThat(call("POST", "/api/customers/1/loyalty-points", "u", "USER", null)).isEqualTo(403); }
    @Test void userMePost_403() throws Exception { assertThat(call("POST", "/api/customers/me", "u", "USER", null)).isEqualTo(403); }
    @Test void partnerRoleList_403() throws Exception { assertThat(call("GET", "/api/customers", "u", "PARTNER", null)).isEqualTo(403); }
    @Test void adminAll_allowed() throws Exception {
        assertThat(call("GET", "/api/customers", "a", "ADMIN", null)).isEqualTo(200);
        assertThat(call("DELETE", "/api/customers/1", "a", "ADMIN", null)).isEqualTo(200);
        assertThat(call("POST", "/api/customers/1/loyalty-points", "a", "ADMIN", null)).isEqualTo(200);
    }
    @Test void internalTokenGet_allowed_butNotWrite() throws Exception {
        assertThat(call("GET", "/api/customers/1", null, null, TOKEN)).isEqualTo(200);
        assertThat(call("DELETE", "/api/customers/1", null, null, TOKEN)).isEqualTo(401);
        assertThat(call("GET", "/api/customers/1", null, null, "wrong")).isEqualTo(401);
    }
    @Test void unrelatedPath_notFiltered() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        req.setRequestURI("/actuator/health");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }
}
