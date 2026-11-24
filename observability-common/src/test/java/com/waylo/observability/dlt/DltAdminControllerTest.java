package com.waylo.observability.dlt;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DltAdminControllerTest {

    private final DltBrowser browser = mock(DltBrowser.class);
    private final DltAdminController controller = new DltAdminController(browser);

    DltAdminControllerTest() {
        when(browser.serviceName()).thenReturn("crm-service");
    }

    @Test
    void nonAdminIsForbiddenForListAndReplay() {
        assertThatThrownBy(() -> controller.list("USER", "crm-service", "order-events", 10))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> controller.list(null, "crm-service", "order-events", 10))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.replay("USER", "crm-service", new DltAdminController.ReplayRequest("order-events", 0, 1L)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verify(browser, never()).replay(anyString(), anyInt(), anyLong());
    }

    @Test
    void adminListsAndReplays() {
        when(browser.peek("order-events", 10)).thenReturn(List.of(Map.of("key", "k")));
        when(browser.replay("order-events", 0, 5L)).thenReturn("order-events");
        assertThat(controller.list("ADMIN", "crm-service", "order-events", 10)).containsEntry("success", true);
        assertThat(controller.replay("ADMIN", "crm-service", new DltAdminController.ReplayRequest("order-events", 0, 5L)))
                .containsEntry("success", true);
    }

    @Test
    void wrongServiceIsNotFoundAndMissingFieldsBadRequest() {
        assertThatThrownBy(() -> controller.list("ADMIN", "other-service", "order-events", 10))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(() -> controller.replay("ADMIN", "crm-service", new DltAdminController.ReplayRequest("order-events", null, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
    }

    @Test
    void payloadIsMaskedAndTruncated() {
        assertThat(DltBrowser.maskAndTruncate("{\"email\":\"john.doe@example.com\"}")).contains("j***@example.").doesNotContain("john.doe");
        assertThat(DltBrowser.maskAndTruncate("x".repeat(1000)).length()).isLessThan(400);
        assertThat(DltBrowser.dltTopic("order-events")).isEqualTo("order-events.DLT");
        assertThat(DltBrowser.dltTopic("order-events.DLT")).isEqualTo("order-events.DLT");
    }
}
