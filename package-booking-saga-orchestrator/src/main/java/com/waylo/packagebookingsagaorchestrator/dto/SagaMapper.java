package com.waylo.packagebookingsagaorchestrator.dto;

import com.waylo.packagebookingsagaorchestrator.domain.SagaInstance;
import com.waylo.packagebookingsagaorchestrator.domain.SagaStep;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SagaMapper {
    @Mapping(target = "steps", source = "steps")
    SagaInstanceResponse toResponse(SagaInstance instance);

    SagaStepResponse toStepResponse(SagaStep step);
    List<SagaStepResponse> toStepResponses(List<SagaStep> steps);
}
