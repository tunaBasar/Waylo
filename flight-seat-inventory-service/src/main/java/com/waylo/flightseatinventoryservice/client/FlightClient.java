package com.waylo.flightseatinventoryservice.client;

import com.waylo.flightseatinventoryservice.dto.ApiResponse;
import com.waylo.flightseatinventoryservice.dto.FlightInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "flight-service", path = "/api/flights")
public interface FlightClient {

    @GetMapping("/number/{flightNumber}")
    ApiResponse<FlightInfo> getByFlightNumber(@PathVariable("flightNumber") String flightNumber);
}
