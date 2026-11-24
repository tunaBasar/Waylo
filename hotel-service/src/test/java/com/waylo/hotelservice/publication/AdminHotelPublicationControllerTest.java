package com.waylo.hotelservice.publication;

import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.publication.PublicationDtos.*;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Admin role checks (U10): anything but the gateway verified ADMIN role is 403 FORBIDDEN and never reaches the service. */
class AdminHotelPublicationControllerTest {

    private final HotelPublicationService service = mock(HotelPublicationService.class);
    private final AdminHotelPublicationController controller = new AdminHotelPublicationController(service);
    private final UUID id = UUID.randomUUID();

    @Test
    void nonAdminOrMissingRole_isForbiddenOnEveryEndpoint() {
        for (String role : new String[]{null, "", "USER", "PARTNER", "admin"}) {
            assertThatThrownBy(() -> controller.list(role, null, null, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.detail(role, id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.decide(id, role, id, new AdminDecisionRequest("APPROVE", null, 0L)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.setStatus(id, role, id, new AdminStatusRequest("SUSPEND", "xxxxxx")))
                    .isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(service);
    }

    @Test
    void admin_reachesService() {
        when(service.adminList("SUBMITTED", null, null, 0, 20)).thenReturn(new AdminPage(java.util.List.of(), 0, 20, 0));
        assertThat(controller.list("ADMIN", "SUBMITTED", null, null, 0, 20).getBody().data().total()).isZero();

        AdminDecisionRequest body = new AdminDecisionRequest("APPROVE", null, 0L);
        controller.decide(id, "ADMIN", id, body);
        verify(service).adminDecide(id, id, body);

        AdminStatusRequest status = new AdminStatusRequest("SUSPEND", "xxxxxx");
        controller.setStatus(id, "ADMIN", id, status);
        verify(service).adminSetStatus(id, id, status);
        verify(service, never()).withdraw(any(), any(), any());
    }
}
