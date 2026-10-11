package com.waylo.checkinservice.dto;

import com.waylo.checkinservice.domain.Checkin;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CheckinMapper {
    CheckinResponse toResponse(Checkin checkin);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "boardingPassUrl", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "checkedInAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Checkin toEntity(CheckinRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "boardingPassUrl", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "checkedInAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(CheckinRequest request, @MappingTarget Checkin checkin);
}
