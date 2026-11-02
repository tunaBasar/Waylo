package com.waylo.flightseatinventoryservice.client;

import com.waylo.flightseatinventoryservice.dto.ApiResponse;
import com.waylo.flightseatinventoryservice.dto.FlightInfo;
import com.waylo.flightseatinventoryservice.dto.FlightSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "flight-service", path = "/api/flights")
public interface FlightClient {

    @GetMapping("/number/{flightNumber}")
    ApiResponse<FlightInfo> getByFlightNumber(@PathVariable("flightNumber") String flightNumber);

    @GetMapping("/number/{flightNumber}")
    ApiResponse<FlightSummary> getSummary(@PathVariable("flightNumber") String flightNumber);

    @GetMapping("/search")
    ApiResponse<List<FlightSummary>> search(@RequestParam("from") String from, @RequestParam("to") String to,
                                            @RequestParam("date") String date);
}
