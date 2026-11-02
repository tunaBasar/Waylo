package com.waylo.packagebookingservice.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
public record BusOrderCreateRequest(@NotNull UUID tripId, @Min(0) int fromSeq, @Min(1) int toSeq,
        @NotNull @Size(min = 1, max = 5) List<@Valid Passenger> passengers,
        @Size(max = 50) String promoCode) {
    public record Passenger(@Min(1) int seatNo, @NotBlank @Size(min = 2, max = 150) String name,
                            @NotBlank @Pattern(regexp = "FEMALE|MALE") String gender, @Size(max = 20) String identityNumber) {}
}
