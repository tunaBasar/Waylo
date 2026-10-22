package com.waylo.flightservice.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class AdminWriteFilterTest {

    private AdminWriteFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new AdminWriteFilter(objectMapper);
    }

    // --- Anon write requests (no X-User-Id) -> 401 ---

    @Test
    void anonPostToFlights_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/flights");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        String body = response.getContentAsString();
        assertThat(body).contains("\"error\":\"UNAUTHORIZED\"");
        assertThat(body).contains("\"status\":401");
    }

    @Test
    void blankUserIdPostToFlights_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/flights");
        request.addHeader("X-User-Id", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void missingUserIdPostToFlights_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/flights");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    // --- Non-admin role on write paths -> 403 ---

    @Test
    void userRolePutToAirports_returns403() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/airports/x");
        request.addHeader("X-User-Id", "user-1");
        request.addHeader("X-User-Role", "USER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
        String body = response.getContentAsString();
        assertThat(body).contains("\"error\":\"FORBIDDEN\"");
    }

    @Test
    void userRolePostToFlightsCancel_returns403() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/flights/x/cancel");
        request.addHeader("X-User-Id", "user-1");
        request.addHeader("X-User-Role", "USER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void adminLowercaseRole_returns403() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/flights/x");
        request.addHeader("X-User-Id", "admin-1");
        request.addHeader("X-User-Role", "admin");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    // --- ADMIN role passes ---

    @Test
    void adminDeleteToFlightsPasses() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/flights/x");
        request.addHeader("X-User-Id", "admin-1");
        request.addHeader("X-User-Role", "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    // --- Safe methods pass without headers ---

    @Test
    void getToFlightsWithoutHeadersPasses() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/flights");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    // --- Non-protected paths pass without headers ---

    @Test
    void postToHotelsWithoutHeadersPasses() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/hotels");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void postToFlightsXWithoutHeadersPasses() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/flightsX");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }
}
