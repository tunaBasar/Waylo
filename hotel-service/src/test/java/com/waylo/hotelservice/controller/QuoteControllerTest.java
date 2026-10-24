package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.dto.QuoteCreateRequest;
import com.waylo.hotelservice.dto.QuoteResponse;
import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.service.QuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuoteControllerTest {

    private final QuoteService quoteService = mock(QuoteService.class);
    private final QuoteController controller = new QuoteController(quoteService);
    private final UUID userId = UUID.randomUUID();
    private final QuoteCreateRequest request = mock(QuoteCreateRequest.class);

    @Test
    void createQuote_admin_forbiddenWithCode() {
        assertThatThrownBy(() -> controller.createQuote(userId, "ADMIN", request))
                .isInstanceOfSatisfying(AccessDeniedException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ADMIN_CANNOT_PURCHASE"));
        verifyNoInteractions(quoteService);
    }

    @Test
    void createQuote_user_allowed() {
        when(quoteService.createQuote(any(), any())).thenReturn(mock(QuoteResponse.class));
        assertThat(controller.createQuote(userId, "USER", request).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
