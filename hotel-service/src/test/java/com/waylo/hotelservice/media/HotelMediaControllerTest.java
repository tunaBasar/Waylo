package com.waylo.hotelservice.media;

import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.media.MediaDtos.StoredFile;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.security.HotelAccessGuard;
import com.waylo.hotelservice.service.HotelService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HotelMediaControllerTest {

    HotelMediaService service = mock(HotelMediaService.class);
    HotelAccessGuard guard = mock(HotelAccessGuard.class);
    HotelService hotelService = mock(HotelService.class);
    HotelRepository hotels = mock(HotelRepository.class);
    HotelMediaController controller = new HotelMediaController(service, guard, hotelService, hotels);
    UUID hotel = UUID.randomUUID();
    UUID media = UUID.randomUUID();

    @Test
    void publicFileSendsPublicFiveMinuteCacheControl() {
        when(hotelService.isPublic(hotel)).thenReturn(true);
        when(service.publicFile(hotel, media, true)).thenReturn(new StoredFile(new byte[] {1}, "image/webp"));
        ResponseEntity<byte[]> r = controller.publicFile(hotel, media);
        assertThat(r.getHeaders().getCacheControl()).contains("public").contains("max-age=300");
        assertThat(r.getHeaders().getContentType().toString()).isEqualTo("image/webp");
        assertThat(r.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void unknownHotelIsTreatedAsNotPublic() {
        when(hotelService.isPublic(hotel)).thenThrow(new ResourceNotFoundException("Hotel", "id", hotel));
        when(service.publicFile(hotel, media, false)).thenThrow(new ResourceNotFoundException("Media", "id", media));
        assertThatThrownBy(() -> controller.publicFile(hotel, media)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void partnerRoutesRunTheAccessGuardFirst() {
        UUID user = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new AccessDeniedException("no")).when(guard).requireHotelManage(user, hotel);
        assertThatThrownBy(() -> controller.listOwn(user, hotel)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.ownFile(user, hotel, media)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }

    @Test
    void adminRoutesRequireAdminRole() {
        assertThatThrownBy(() -> controller.adminList("USER", "PENDING", null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.adminList(null, "PENDING", null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.adminFile("PARTNER", media)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.review(UUID.randomUUID(), "USER", media, new MediaDtos.ReviewRequest("APPROVE", null)))
                .isInstanceOf(AccessDeniedException.class);
        verify(service, never()).review(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
