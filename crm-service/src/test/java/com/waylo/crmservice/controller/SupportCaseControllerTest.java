package com.waylo.crmservice.controller;

import com.waylo.crmservice.domain.SupportCase;
import com.waylo.crmservice.dto.SupportDtos.Capabilities;
import com.waylo.crmservice.dto.SupportDtos.MessageRequest;
import com.waylo.crmservice.exception.AccessDeniedException;
import com.waylo.crmservice.service.SupportCaseService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SupportCaseControllerTest {

    private final SupportCaseService service = mock(SupportCaseService.class);
    private final SupportCaseController controller = new SupportCaseController(service);
    private final UUID user = UUID.randomUUID();

    @Test
    void capabilitiesAreExplicitAndHaveNoFakeHours() {
        Capabilities c = controller.capabilities().getBody().data();
        assertThat(c.liveChat()).isFalse();
        assertThat(c.channels()).isEmpty();
        assertThat(c.hours()).isNull();
        assertThat(c.decisionPending()).isTrue();
    }

    @Test
    void adminEndpointsRejectNonAdmin() {
        UUID id = UUID.randomUUID();
        for (String role : new String[]{null, "USER", "PARTNER"}) {
            assertThatThrownBy(() -> controller.adminList(user, role, null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.adminGet(user, role, id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.adminReply(user, role, id, new MessageRequest("x"))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.adminClose(user, role, id)).isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(service);
    }

    @Test
    void adminListFiltersByStatus() {
        when(service.listAll(SupportCase.Status.OPEN)).thenReturn(List.of());
        assertThat(controller.adminList(user, "ADMIN", SupportCase.Status.OPEN).getStatusCode().value()).isEqualTo(200);
        verify(service).listAll(SupportCase.Status.OPEN);
    }
}
