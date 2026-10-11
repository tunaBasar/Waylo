package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.Hotel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface HotelMapper {

    @Mapping(target = "roomCount", expression = "java(hotel.getRooms() != null ? hotel.getRooms().size() : 0)")
    HotelResponse toResponse(Hotel hotel);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "rooms", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Hotel toEntity(HotelRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "rooms", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(HotelRequest request, @MappingTarget Hotel hotel);
}
