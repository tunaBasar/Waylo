package com.waylo.flightseatinventoryservice.dto;

import com.waylo.flightseatinventoryservice.domain.FlightSeat;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FlightSeatMapper {

    FlightSeatResponse toResponse(FlightSeat seat);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "available", constant = "true")
    @Mapping(target = "reservationId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    FlightSeat toEntity(FlightSeatRequest request);
}
