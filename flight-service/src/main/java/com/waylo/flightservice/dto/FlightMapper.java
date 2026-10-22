package com.waylo.flightservice.dto;

import com.waylo.flightservice.domain.Flight;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface FlightMapper {

    @Mapping(source = "departureAirport.iataCode", target = "departureAirportCode")
    @Mapping(source = "departureAirport.name", target = "departureAirportName")
    @Mapping(source = "arrivalAirport.iataCode", target = "arrivalAirportCode")
    @Mapping(source = "arrivalAirport.name", target = "arrivalAirportName")
    FlightResponse toResponse(Flight flight);

    /**
     * Entity olusturma: departureAirport ve arrivalAirport service katmaninda
     * IATA code ile lookup yapilip set edilecek, burada ignore.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "departureAirport", ignore = true)
    @Mapping(target = "arrivalAirport", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    Flight toEntity(FlightRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "departureAirport", ignore = true)
    @Mapping(target = "arrivalAirport", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(FlightRequest request, @MappingTarget Flight flight);
}
