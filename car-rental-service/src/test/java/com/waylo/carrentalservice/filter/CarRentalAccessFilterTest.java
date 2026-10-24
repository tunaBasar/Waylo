package com.waylo.carrentalservice.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CarRentalAccessFilterTest {
    private final CarRentalAccessFilter filter = new CarRentalAccessFilter(new ObjectMapper());

    private MockHttpServletResponse run(String method, String path, String id, String role) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (id != null) request.addHeader("X-User-Id", id);
        if (role != null) request.addHeader("X-User-Role", role);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test void vehicleGetOpen() throws Exception { assertThat(run("GET", "/api/vehicles", null, null).getStatus()).isEqualTo(200); }
    @Test void vehicleWriteWithoutIdUnauthorized() throws Exception { assertThat(run("POST", "/api/vehicles", null, "ADMIN").getStatus()).isEqualTo(401); }
    @Test void vehicleWriteUserForbidden() throws Exception { assertThat(run("PUT", "/api/vehicles/1", UUID.randomUUID().toString(), "USER").getStatus()).isEqualTo(403); }
    @Test void vehicleWriteAdminPasses() throws Exception { assertThat(run("DELETE", "/api/vehicles/1", UUID.randomUUID().toString(), "ADMIN").getStatus()).isEqualTo(200); }
    @Test void rentalWithoutIdUnauthorized() throws Exception { assertThat(run("GET", "/api/rentals", null, null).getStatus()).isEqualTo(401); }
    @Test void rentalWithIdPasses() throws Exception { assertThat(run("POST", "/api/rentals", UUID.randomUUID().toString(), "USER").getStatus()).isEqualTo(200); }
    @Test void rentalCreateAdminForbidden() throws Exception {
        MockHttpServletResponse r = run("POST", "/api/rentals", UUID.randomUUID().toString(), "ADMIN");
        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(r.getContentAsString()).contains("ADMIN_CANNOT_PURCHASE");
    }
    @Test void rentalAdminKeepsReadAndCancel() throws Exception {
        assertThat(run("GET", "/api/rentals", UUID.randomUUID().toString(), "ADMIN").getStatus()).isEqualTo(200);
        assertThat(run("POST", "/api/rentals/abc/cancel", UUID.randomUUID().toString(), "ADMIN").getStatus()).isEqualTo(200);
    }
    @Test void vehiclePrefixLookalikeNotMatched() throws Exception { assertThat(run("POST", "/api/vehiclesX", null, null).getStatus()).isEqualTo(200); }
}
