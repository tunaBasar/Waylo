package com.waylo.packagebookingservice.controller;

import com.waylo.packagebookingservice.dto.PartnerOrderItemPage;
import com.waylo.packagebookingservice.service.PartnerOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PartnerOrderControllerTest {

    @Test
    void delegatesWithHeaderUserAndPathOrg() {
        var service = mock(PartnerOrderService.class);
        var controller = new PartnerOrderController(service);
        UUID user = UUID.randomUUID(), org = UUID.randomUUID();
        var page = new PartnerOrderItemPage(List.of(), 0, 20, 0);
        when(service.list(user, org, null, null, null, 0, 20)).thenReturn(page);

        var res = controller.list(user, org, null, null, null, 0, 20);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().data()).isSameAs(page);
    }
}
