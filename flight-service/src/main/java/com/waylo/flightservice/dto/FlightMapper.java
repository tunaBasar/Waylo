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
     * Entity oluşturma: departureAirport ve arrivalAirport service katmanında
     * IATA code ile lookup yapılıp set edilecek, burada ignore.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "departureAirport", ignore = true)
    @Mapping(target = "arrivalAirport", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Flight toEntity(FlightRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "departureAirport", ignore = true)
    @Mapping(target = "arrivalAirport", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(FlightRequest request, @MappingTarget Flight flight);
}
