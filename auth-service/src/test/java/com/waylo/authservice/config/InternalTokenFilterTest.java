package com.waylo.authservice.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalTokenFilterTest {

    private static final String VALID_TOKEN = "a".repeat(32);
    private static final String WRONG_TOKEN = "wrong".repeat(7);

    private InternalTokenFilter filter;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new InternalTokenFilter(VALID_TOKEN);
        response = new MockHttpServletResponse();
    }

    @Test
    void correctToken_passesThrough() throws ServletException, IOException {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/internal/access");
        request.addHeader("X-Internal-Token", VALID_TOKEN);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void missingToken_rejectsWith401() throws ServletException, IOException {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/internal/access");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED_SERVICE");
    }

    @Test
    void wrongToken_rejectsWith401() throws ServletException, IOException {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/internal/access");
        request.addHeader("X-Internal-Token", WRONG_TOKEN);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void nonInternalPath_skipsCheck() throws ServletException, IOException {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/login");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shortToken_throwsIllegalStateException() {
        assertThatThrownBy(() -> new InternalTokenFilter("short"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emptyToken_throwsIllegalStateException() {
        assertThatThrownBy(() -> new InternalTokenFilter(""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullToken_throwsIllegalStateException() {
        assertThatThrownBy(() -> new InternalTokenFilter((String) null))
                .isInstanceOf(IllegalStateException.class);
    }
}
