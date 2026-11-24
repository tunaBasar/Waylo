package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.PublicationStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** U12: a client can never set publication state through HotelRequest, the response carries it. */
class HotelMapperPublicationTest {

    private final HotelMapper mapper = Mappers.getMapper(HotelMapper.class);

    @Test
    void toEntity_alwaysDraft() {
        Hotel hotel = mapper.toEntity(new HotelRequest("n", "c", "k", "a", 3, null, null, null, UUID.randomUUID()));
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.DRAFT);
        assertThat(hotel.getPublishedAt()).isNull();
        assertThat(hotel.getContentVersion()).isZero();
    }

    @Test
    void updateEntity_doesNotTouchPublicationFields() {
        Hotel hotel = new Hotel();
        hotel.setPublicationStatus(PublicationStatus.PUBLISHED);
        hotel.setContentVersion(4);
        mapper.updateEntity(new HotelRequest("n", "c", "k", "a", 3, null, null, null, UUID.randomUUID()), hotel);
        assertThat(hotel.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(hotel.getContentVersion()).isEqualTo(4);
        assertThat(hotel.getOrganizationId()).isNull();
    }

    @Test
    void response_carriesPublicationStatusAndContentVersion() {
        Hotel hotel = new Hotel();
        hotel.setPublicationStatus(PublicationStatus.SUSPENDED);
        hotel.setContentVersion(7);
        HotelResponse response = mapper.toResponse(hotel);
        assertThat(response.publicationStatus()).isEqualTo("SUSPENDED");
        assertThat(response.contentVersion()).isEqualTo(7L);
        assertThat(response.withReviews(4.5, 3).publicationStatus()).isEqualTo("SUSPENDED");
    }
}
