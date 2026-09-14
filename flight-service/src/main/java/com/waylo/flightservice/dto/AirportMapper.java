package com.waylo.flightservice.dto;

import com.waylo.flightservice.domain.Airport;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AirportMapper {

    AirportResponse toResponse(Airport airport);

    Airport toEntity(AirportRequest request);

    void updateEntity(AirportRequest request, @MappingTarget Airport airport);
}
